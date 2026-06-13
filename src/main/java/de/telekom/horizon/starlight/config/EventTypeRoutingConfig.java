// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for content-based event-type routing applied at publish time.
 *
 * <p>When enabled, {@link de.telekom.horizon.starlight.service.EventTypeRoutingService} inspects an
 * incoming event's payload metadata and, if a rule matches, rewrites {@code event.type} <em>before</em>
 * the event is written to Kafka. This lets the multiplexer (Galaxy) route the event by type — a cheap
 * indexed lookup — instead of evaluating an expensive per-subscription content filter against the full
 * payload for every event.
 *
 * <p>The primary use case is high-volume Spectre "wiretap" events (originally published under the generic
 * type {@code de.telekom.ei.listener} by the {@code gateway} publisher): a top-talker can be peeled off
 * onto its own dedicated event type so Galaxy never filters it.
 *
 * <p>Bound from the {@code starlight.event-type-routing} configuration tree (auto-registered via
 * {@code @ConfigurationPropertiesScan} on {@code StarlightApplication}). Disabled by default, in which case
 * publishing behaves exactly as before. See {@code docs/event-type-routing.md}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "starlight.event-type-routing")
public class EventTypeRoutingConfig {

    /**
     * Master switch. When {@code false} (default) routing is a no-op and the publish path is unchanged.
     */
    private boolean enabled = false;

    /**
     * Only events published by this publisher (the OAuth2 {@code clientId} claim) are eligible for routing.
     * Spectre/Jumper publishes with {@code clientId=gateway}. A blank value disables the publisher gate
     * (routing then applies regardless of publisher).
     */
    private String publisherId = "gateway";

    /**
     * Only events whose original {@code type} starts with this prefix are eligible — a cheap pre-filter so
     * non-Spectre traffic is never inspected. Spectre wiretap events use {@code de.telekom.ei.listener}.
     * A blank value disables the type gate.
     */
    private String applicableTypePrefix = "de.telekom.ei.listener";

    /**
     * Ordered routing rules; the first rule whose {@link RoutingRule#getMatch() match} conditions all hold
     * wins, and the event type is rewritten to its {@link RoutingRule#getTargetType() target-type}. Typically
     * supplied via config-map / Helm values rather than environment variables.
     */
    private List<RoutingRule> rules = new ArrayList<>();

    /**
     * A single content-based routing rule: a target event type plus a set of equality conditions
     * (AND-ed together) evaluated against the event payload.
     */
    @Getter
    @Setter
    public static class RoutingRule {

        /**
         * The event type to rewrite to when this rule matches. Must satisfy the Horizon event-type charset
         * ({@code [a-zA-Z0-9.-]}) and must have a Subscription that authorises the configured publisher, or
         * Starlight will drop/reject the publish.
         */
        private String targetType;

        /**
         * Equality conditions, AND-ed together, evaluated against {@code event.data}. Keys are dot-paths into
         * the (deserialised JSON) payload — e.g. {@code issue}, {@code provider}, {@code consumer},
         * {@code method}, or a nested {@code header.x-some-header}. Values are compared as strings.
         *
         * <p>Each condition is <b>optional</b>: only the keys you specify are evaluated; an unspecified key
         * (e.g. omit {@code consumer}) is not used as a criterion. An empty or omitted map therefore matches
         * <b>every gated event</b> — use it deliberately as a catch-all / default target, and (since the first
         * matching rule wins) place such a rule last.
         */
        private Map<String, String> match = new LinkedHashMap<>();
    }
}
