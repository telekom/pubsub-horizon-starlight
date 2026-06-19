// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config.spectre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Spectre direct-publish applied at publish time.
 *
 * <p>When enabled, {@link de.telekom.horizon.starlight.service.SpectreDirectPublishService}
 * inspects an incoming Spectre "wiretap" event and, if a rule matches, rewrites {@code event.type}
 * <em>before</em> the event is written to Kafka. This lets the multiplexer (Galaxy) route the event
 * by type — a cheap indexed lookup — directly to the team's dedicated, filter-less subscription,
 * instead of fanning it out across the generic {@code de.telekom.ei.listener} stream, running a
 * per-subscription JsonPath content filter against the full payload, and then looping the
 * per-listener delivery back through the producer's {@code auto_event_route_post} re-publish.
 *
 * <p>Bound from the {@code starlight.spectre.direct-publish} configuration tree (auto-registered
 * via {@code @ConfigurationPropertiesScan} on {@code StarlightApplication}). Disabled by default,
 * in which case publishing behaves exactly as before. Configuration is validated at startup — a
 * blank {@link #applicableType} or an incompletely specified rule fails the boot. See {@code
 * docs/spectre-direct-publish.md}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "starlight.spectre.direct-publish")
public class SpectreDirectPublishConfiguration {

    /**
     * Master switch. When {@code false} (default) direct-publish is a no-op and the publish path is
     * unchanged.
     */
    private boolean enabled = false;

    /**
     * Only events published by this publisher (the OAuth2 {@code clientId} claim) are eligible.
     * Jumper publishes with {@code clientId=gateway}. Matched for exact equality; must <b>not</b> be
     * blank — a blank/unset value fails startup validation. Kept as the safety filter so non-gateway
     * traffic is never touched.
     */
    @NotBlank private String publisherId = "gateway";

    /**
     * The event-type gate: only events whose original {@code type} <b>exactly equals</b> this value
     * are considered. Defaults to the generic Spectre type {@code de.telekom.ei.listener}. Must
     * <b>not</b> be blank — a blank/unset value fails startup validation.
     */
    @NotBlank private String applicableType = "de.telekom.ei.listener";

    /**
     * Spectre selections to direct-publish; the first rule whose {@code issue}, {@code consumer}
     * and {@code provider} all match wins, and the event type is rewritten to its {@link
     * SpectreDirectPublishRule#getTargetEventType() target-event-type}. Each rule is fully
     * validated at startup (all four fields required). Typically supplied via config-map / Helm
     * values rather than environment variables.
     */
    private List<@Valid SpectreDirectPublishRule> rules = new ArrayList<>();
}
