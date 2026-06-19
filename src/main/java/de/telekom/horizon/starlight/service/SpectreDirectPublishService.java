// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsHelper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.horizon.starlight.config.spectre.SpectreDirectPublishConfiguration;
import de.telekom.horizon.starlight.config.spectre.SpectreDirectPublishRule;

import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Direct-publishes high-volume Spectre "wiretap" events to a dedicated event type at publish time.
 *
 * <p>For an eligible event (feature enabled, matching publisher, original type equal to the
 * configured {@code applicable-type} gate), the first matching {@link SpectreDirectPublishRule}
 * rewrites {@code event.type} in place. When nothing matches (or the feature is disabled) the event
 * is left untouched and follows the existing flow, so the feature is strictly additive and safe to
 * enable incrementally.
 *
 * @see SpectreDirectPublishConfiguration
 */
@Service
@Slf4j
public class SpectreDirectPublishService {

    /**
     * Counter incremented once per successful event-type rewrite. Tagged by {@code
     * target_event_type} so the direct-published volume per dedicated type is observable in Grafana
     * (debug logging alone is not queryable at the volumes this feature targets).
     */
    static final String METRIC_DIRECT_PUBLISH = "spectre_direct_publish";

    /**
     * Diagnostic counter incremented once per event whose {@code issue} matches a configured rule
     * but whose {@code consumer}/{@code provider} do not. This points to misconfiguration, or a
     * second listener on that base-path that would stop receiving events.The metric is tagged with
     * the event's actual {@code issue}, {@code consumer} and {@code provider} so the stray
     * selection is identifiable.
     */
    static final String METRIC_DIRECT_PUBLISH_UNMATCHED = "spectre_direct_publish_unmatched";

    static final String TAG_TARGET_EVENT_TYPE = "target_event_type";
    static final String TAG_ISSUE = "issue";
    static final String TAG_CONSUMER = "consumer";
    static final String TAG_PROVIDER = "provider";

    private static final String FIELD_ISSUE = "issue";
    private static final String FIELD_CONSUMER = "consumer";
    private static final String FIELD_PROVIDER = "provider";

    private final SpectreDirectPublishConfiguration config;

    private final HorizonMetricsHelper metricsHelper;

    public SpectreDirectPublishService(
            SpectreDirectPublishConfiguration config, HorizonMetricsHelper metricsHelper) {
        this.config = config;
        this.metricsHelper = metricsHelper;
    }

    /**
     * Rewrites {@code event.type} in place when an enabled direct-publish rule fully matches.
     *
     * <p>No-op unless the feature is enabled, {@code publisherId} exactly equals the configured
     * publisher, and the event's current type exactly equals the configured {@code applicable-type}
     * gate. Rules are evaluated in order; the first rule whose {@code issue}, {@code consumer} and
     * {@code provider} all match wins. When the {@code issue} matches a rule but the {@code
     * consumer}/{@code provider} do not, a diagnostic counter is incremented (no rewrite, no error
     * log).
     *
     * @param event the event being published (mutated in place on a full match); never {@code null}
     *     and with a non-blank type, as enforced by upstream validation in {@code EventController}
     * @param publisherId the publisher id (OAuth2 {@code clientId}) of the caller
     */
    public void rewriteTypeForDirectPublish(@NonNull Event event, String publisherId) {
        if (!config.isEnabled()) {
            return;
        }
        if (!config.getPublisherId().equals(publisherId)) {
            return;
        }
        if (!config.getApplicableType().equals(event.getType())) {
            return;
        }

        if (!(event.getData() instanceof Map<?, ?> data)) {
            // Spectre events carry a JSON object payload; anything else has no selection fields.
            return;
        }
        // Fail fast: each selection field must be present as a plain string.
        if (!(data.get(FIELD_ISSUE) instanceof String issue)) {
            return;
        }
        if (!(data.get(FIELD_CONSUMER) instanceof String consumer)) {
            return;
        }
        if (!(data.get(FIELD_PROVIDER) instanceof String provider)) {
            return;
        }

        boolean issueOnlyHit = false;
        for (SpectreDirectPublishRule rule : config.getRules()) {
            if (!rule.getIssue().equals(issue)) {
                continue;
            }
            if (rule.getConsumer().equals(consumer) && rule.getProvider().equals(provider)) {
                var targetEventType = rule.getTargetEventType();
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Direct-publishing event id={} from type '{}' to '{}' (publisherId={})",
                            event.getId(),
                            event.getType(),
                            targetEventType,
                            publisherId);
                }
                event.setType(targetEventType);
                metricsHelper
                        .getRegistry()
                        .counter(METRIC_DIRECT_PUBLISH, TAG_TARGET_EVENT_TYPE, targetEventType)
                        .increment();
                return; // first match wins
            }
            issueOnlyHit = true;
        }

        if (issueOnlyHit) {
            // A configured base-path is carrying traffic from a consumer/provider we did not
            // configure.
            // Surface it as a metric (not a log line) so it is queryable without flooding logs at
            // volume.
            metricsHelper
                    .getRegistry()
                    .counter(
                            METRIC_DIRECT_PUBLISH_UNMATCHED,
                            TAG_ISSUE,
                            issue,
                            TAG_CONSUMER,
                            consumer,
                            TAG_PROVIDER,
                            provider)
                    .increment();
        }
    }
}
