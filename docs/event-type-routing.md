<!--
Copyright 2026 Deutsche Telekom IT GmbH

SPDX-License-Identifier: Apache-2.0
-->

# Content-based event-type routing

## Why

The multiplexer (Galaxy) fans every published event out to **all** subscriptions on its `(environment, type)`
and then runs each subscription's content filter against the full payload. For high-volume Spectre
"wiretap" events — all published under the single generic type `de.telekom.ei.listener` — that means the
top-talker's firehose is JsonPath-evaluated against every listener's filter, repeatedly, per event.

This feature lets Starlight **rewrite `event.type` at publish time** based on the event's metadata, so a
top-talker can be peeled onto its **own dedicated type**. Galaxy then routes it by type (a cheap indexed
lookup) to a single, filter-less subscription — eliminating the expensive per-subscription filtering for
that stream and removing its volume from the shared `de.telekom.ei.listener` fan-out.

The routing decision is made **once**, here, on the already-deserialised payload — never re-parsed — instead
of `N subscriptions × K filter-leaves` times in the multiplexer.

## Where it runs

`EventTypeRoutingService.applyRouting(...)` is invoked as the **first step** of `PublisherService.publish`,
*before* `checkEventTypeOwnership`. So the **rewritten** type is what gets authorised and published — which
means a routed type must have a Subscription that authorises the publisher (see [Cutover](#cutover-and-safety)).

## Configuration

Disabled by default; when disabled the publish path is byte-for-byte unchanged. Bound from the
`starlight.event-type-routing` tree.

| Key | Env var | Default | Meaning |
|-----|---------|---------|---------|
| `enabled` | `STARLIGHT_EVENT_TYPE_ROUTING_ENABLED` | `false` | Master switch. |
| `publisher-id` | `STARLIGHT_EVENT_TYPE_ROUTING_PUBLISHER_ID` | `gateway` | Only route events from this publisher (OAuth2 `clientId`). Blank = any publisher. |
| `applicable-type-prefix` | `STARLIGHT_EVENT_TYPE_ROUTING_APPLICABLE_TYPE_PREFIX` | `de.telekom.ei.listener` | Only events whose original type starts with this prefix are eligible. Blank = any type. |
| `rules` | — (config-map / Helm values) | `[]` | Ordered rules; **first match wins**. |

Each rule has a `target-type` and a `match` map of equality conditions (AND-ed), evaluated against
`event.data`. **Each condition is optional**: only the keys you specify are evaluated; an unspecified key
(e.g. omit `consumer`) is not used as a criterion. An **empty or omitted `match` matches every gated
event** — use it deliberately as a catch-all / default target, and (first match wins) place it last.
Supply `rules` via a config-map–mounted `application.yaml` overlay or Helm values:

```yaml
starlight:
  event-type-routing:
    enabled: true
    publisher-id: gateway
    applicable-type-prefix: de.telekom.ei.listener
    rules:
      - target-type: de.telekom.ei.top-talker
        match:
          issue: <top-talker-issue>            # the tapped API / issue
          provider: <top-talker-service-owner> # optional extra condition (AND)
      - target-type: de.telekom.ei.othertalker
        match:
          issue: <other-issue>
```

The simple toggles are env-var friendly; the structured `rules` list is intended for config-map/Helm
(env-var binding of a list of objects is impractical).

## Routing metadata set

`match` keys are **dot-paths into `event.data`**, which for a Spectre event is the `SpectreData` object.
The fields available to route on:

| Path (into `event.data`) | Source (SpectreData) | Cardinality | Present on | Recommended for routing |
|--------------------------|----------------------|-------------|------------|--------------------------|
| `issue` | the tapped API base-path / event type | low | REQUEST + RESPONSE | **Yes — primary key** |
| `provider` | `serviceOwner` of the tapped API | low | REQUEST + RESPONSE | Yes (narrow a provider) |
| `consumer` | calling app (token `clientId`) | medium | REQUEST + RESPONSE | Yes (per-consumer split) |
| `method` | HTTP method (`GET`/`POST`/…) | low | REQUEST + RESPONSE | Situational |
| `kind` | `REQUEST` / `RESPONSE` | 2 | REQUEST + RESPONSE | Only to deliberately split request vs response |
| `status` | HTTP status code | low | **RESPONSE only** | Avoid — asymmetric (splits a call's two events) |
| `header.<name>` | a request/response header | varies | depends | Situational; values compared as strings |
| `parameters.<name>` | a query parameter | varies | **REQUEST only** | Avoid — request-only |
| `payload.<field>` | a body field (JSON payloads only) | varies | both if JSON | Content routing; beware non-JSON (base64) + high cardinality |

**Guidance**

- **Prefer `issue`** (optionally `+ provider` / `+ consumer`). It's low-cardinality and present on both the
  REQUEST and RESPONSE events of a call, so both land on the same dedicated type.
- **Route REQUEST and RESPONSE consistently** — match on a field present on *both*. Avoid `status` and
  `parameters.*` (response-only / request-only) or a call's two events diverge.
- **Keep the routing key low-cardinality.** One dedicated type per API/listener is fine; routing on an
  unbounded field (ids in `payload.*`) would explode the number of types/subscriptions/topics.
- Values are compared as **strings** (`String.valueOf`), so configure `status: "200"`, not `200`.

## Cutover and safety

1. **Provision the subscription first.** Create a subscription on the new `target-type` with
   `publisherId` including the routing publisher (`gateway`) and the listener's callback/SSE, **no filter**.
2. **Then enable the rule.** Until a `target-type` has a subscription, Starlight's ownership check returns
   `202` (unknown type / no subscription) and the event is dropped — so order matters.
3. **Fallback is automatic.** Anything not matched keeps its original type and follows the existing
   filter + round-trip path, so you can migrate one top-talker at a time with zero risk to the long tail.
4. The `target-type` must satisfy the Horizon event-type charset `[a-zA-Z0-9.-]`.

## Behaviour summary

- Disabled, wrong publisher, non-applicable type, or no matching rule → event untouched.
- A match → `event.type` is rewritten in place; first match wins.
- Null / non-object / missing payload paths are safe (treated as non-matches).
