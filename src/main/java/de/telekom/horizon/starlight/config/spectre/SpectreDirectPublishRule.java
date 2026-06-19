// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config.spectre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/**
 * A single Spectre direct-publish rule: a fully-specified Spectre selection (tapped API base-path
 * plus the consuming and providing apps) and the dedicated event type its events should be
 * published under.
 *
 * <p>All four fields are <b>required</b>; an incompletely specified rule fails validation at
 * startup (see {@link SpectreDirectPublishConfiguration}). Matching is exact equality against the
 * top-level {@code issue}, {@code consumer} and {@code provider} fields of a Spectre event's {@code
 * event.data} ({@code SpectreData}).
 *
 * @see SpectreDirectPublishConfiguration
 */
@Getter
@Setter
public class SpectreDirectPublishRule {

    /**
     * The dedicated event type to rewrite to when this rule matches. Must satisfy the Horizon
     * event-type charset ({@code [a-zA-Z0-9.-]}) and must have a Subscription that authorises the
     * configured publisher, or Starlight will drop/reject the publish (see the cutover notes in
     * {@code docs/spectre-direct-publish.md}).
     */
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9.-]+")
    private String targetEventType;

    /**
     * The tapped API base-path. Matched for exact equality against {@code event.data.issue} (which,
     * for REST wiretaps, is the gateway's {@code apiBasePath}).
     */
    @NotBlank private String issue;

    /**
     * The consuming app id (the caller's token {@code clientId}). Matched for exact equality
     * against {@code event.data.consumer}.
     */
    @NotBlank private String consumer;

    /**
     * The providing app id (the {@code serviceOwner} of the tapped API). Matched for exact equality
     * against {@code event.data.provider}.
     */
    @NotBlank private String provider;
}
