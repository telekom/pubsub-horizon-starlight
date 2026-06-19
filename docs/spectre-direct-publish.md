<!--
Copyright 2026 Deutsche Telekom IT GmbH

SPDX-License-Identifier: Apache-2.0
-->

# Spectre direct-publish

## Why

Spectre "wiretap" events are **all** published under the single generic event type
`de.telekom.ei.listener`. Galaxy (the multiplexer) fans every such event out to **all** listener
subscriptions on that `(environment, type)` and runs each subscription's JsonPath content filter against
the full payload — `O(payload × subscriptions × filter-leaves)`. The per-listener delivery then calls back
to the producer (Jumper's `auto_event_route_post` path), which rewrites the type to the team-specific
`de.telekom.ei.listener.<appId>` and **republishes** — a second full trip through Horizon. Under peak load a
high-volume listener's firehose saturates Galaxy and backs up the shared Horizon event queues.

This feature is a **load mitigation: it publishes a configured high-volume Spectre selection directly to its
team-specific event type to avoid the expensive multiplexing and the republish loop.** Starlight **rewrites
`event.type` at publish time** based on the event's metadata, so a configured top-talker is published
straight to its **own dedicated type** at ingest. Galaxy then routes it by type (a cheap indexed lookup) to
a single, filter-less subscription — skipping the generic-type fan-out / JsonPath filtering **and** the
`auto_event_route_post` round-trip + second publish. The decision is made **once**, here, on the
already-deserialised payload — never re-parsed.

The feature is intentionally scoped specifically to Spectre traffic, but could be generalized in the future
if necessary — e.g. by replacing the fixed `issue` / `consumer` / `provider` selection with generic content
filtering.

```
Before (generic fan-out + republish):

  publish  ─►  de.telekom.ei.listener  ─►  Galaxy fans out to ALL listener subs
                                              │   (JsonPath content filter per sub, full payload)
                                              ▼
                                       per-listener delivery
                                              │
                                              ▼
                                  auto_event_route_post  ─►  re-publish as
                                  (second full Horizon trip)   de.telekom.ei.listener.<appId>

After (direct-publish for a configured selection):

  publish  ─►  applicable-type gate  ─►  rule match (issue+consumer+provider)
                                              │
                                              ▼
                                  event.type := de.telekom.ei.listener.<appId>
                                              │
                                              ▼
                            Galaxy routes BY TYPE to the single filter-less sub
                            (no generic fan-out, no content filter, no republish)
```

## Configuration

The feature is disabled by default

| Key | Env var | Default | Meaning |
|-----|---------|---------|---------|
| `enabled` | `STARLIGHT_SPECTRE_DIRECT_PUBLISH_ENABLED` | `false` | Master switch. |
| `publisher-id` | `STARLIGHT_SPECTRE_DIRECT_PUBLISH_PUBLISHER_ID` | `gateway` | Only events from this publisher (OAuth2 `clientId`) are eligible (exact match). **Must not be blank.** |
| `applicable-type` | `STARLIGHT_SPECTRE_DIRECT_PUBLISH_APPLICABLE_TYPE` | `de.telekom.ei.listener` | The event-type gate (exact equality). **Must not be blank.** |
| `rules` | — (config-map / Helm values) | `[]` | Spectre selections to direct-publish; **first full match wins**. |

### The event-type gate

An event is only considered when its original type **exactly equals** `applicable-type`. The default is the
generic Spectre type `de.telekom.ei.listener`; this **excludes** the per-subscription auto-event
re-publishes (`de.telekom.ei.listener.<subscriberId>`) by construction, so a re-published event that has
already been demultiplexed is never re-evaluated. A **blank `applicable-type` is rejected at startup** — the
gate is mandatory, there is no "wide open" mode.

### Rules

Each rule is a fully-specified Spectre selection plus the dedicated event type to publish it under. **All
four fields are required**; an incomplete rule **fails startup validation** (the application does not boot):

| Field | Matched against | Notes |
|-------|-----------------|-------|
| `target-event-type` | — | The dedicated type to rewrite to. Must satisfy the Horizon event-type charset `[a-zA-Z0-9.-]`. |
| `issue` | `event.data.issue` | The tapped API base-path. |
| `consumer` | `event.data.consumer` | The consuming app id (token `clientId`). |
| `provider` | `event.data.provider` | The providing app id (`serviceOwner`). |

A rule matches when `issue`, `consumer` **and** `provider` all equal (exact string equality) the
corresponding top-level fields of the event's `SpectreData`. Rules are evaluated in order and the **first
full match wins**. Supply `rules` via a config-map–mounted `application.yaml` overlay or Helm values:

```yaml
starlight:
  spectre:
    direct-publish:
      enabled: true
      publisher-id: gateway
      applicable-type: de.telekom.ei.listener
      rules:
        - target-event-type: de.telekom.ei.listener.eni--example-team--example-listener
          issue: /eni/example/v1
          consumer: eni--example-consumer--example-app
          provider: eni--example-provider--example-app
        - target-event-type: de.telekom.ei.listener.eni--example-team--example-listener
          issue: /eni/example-provisioning/v1
          consumer: eni--example-consumer--example-app
          provider: eni--example-provider--example-app
```

The `rules` list can be supplied two ways:

**Option 1 — config-map / Helm values**: mount an `application.yaml` overlay or implement a dedicated
Helm values block.

**Option 2 — environment variables**: bind via Spring Boot [relaxed binding](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables)
using a numeric index (`_<index>_`); indices must be contiguous from `0`:

```bash
STARLIGHT_SPECTRE_DIRECT_PUBLISH_RULES_0_TARGETEVENTTYPE=de.telekom.ei.listener.eni--example-team--example-listener
STARLIGHT_SPECTRE_DIRECT_PUBLISH_RULES_0_ISSUE=/eni/example/v1
STARLIGHT_SPECTRE_DIRECT_PUBLISH_RULES_0_CONSUMER=eni--example-consumer--example-app
STARLIGHT_SPECTRE_DIRECT_PUBLISH_RULES_0_PROVIDER=eni--example-provider--example-app
```

> **`issue` is the gateway's API base-path.** For REST wiretaps the publisher (Jumper) sets `data.issue`
> from the route listener's `issue`, which is the tapped API base-path — i.e. the same value a gateway-side
> rule matches as `apiBasePath` (confirmed in Jumper). For pub/sub listeners `issue` is the event type
> instead.

## Diagnostics

When an event's `issue` matches a configured rule but its `consumer`/`provider` do **not**, the event is
left untouched and the counter **`spectre_direct_publish_unmatched`** is incremented, tagged with the
event's actual **`issue`**, **`consumer`** and **`provider`**.

A non-zero `spectre_direct_publish_unmatched` metric always points to a **misconfiguration**. A direct-published
base-path can only be routed to a **single** Spectre listener (the one dedicated `target-event-type`), so any
traffic on a configured base-path from a consumer/provider tuple you did not configure cannot be served
correctly — it is either a wrong/incomplete rule, or a **second listener** on that base-path that would
silently stop receiving events.

## Metrics

| Metric | Tags | When |
|--------|------|------|
| `spectre_direct_publish` | `target_event_type` | Once per successful rewrite. |
| `spectre_direct_publish_unmatched` | `issue`, `consumer`, `provider` | Once per event whose `issue` matched a rule but `consumer`/`provider` did not. |

## Known limitations

These are inherent to rewriting at publish time and have been evaluated and accepted for the Spectre
peak-load mitigation use case:

- **Spectre content/trigger filters are not evaluated for directly published events.** When a Spectre
  listener is set up with a content-type or trigger filter, that filter is evaluated by Galaxy on the
  **generic** `de.telekom.ei.listener` type. Directly published events bypass that generic-type stage — they
  are routed by their dedicated `target-event-type` instead — so any such filter is **not** applied to them.
  Only direct-publish a selection whose events should be delivered in full.
- **Basepaths configured for direct publish can only have a single listener.** If there is a second listener
  configured, events rerouted using direct-publish to the first listener will no longer reach it. Before
  enabling direct-publish for a basepath, confirm that no other listeners exist for it.
