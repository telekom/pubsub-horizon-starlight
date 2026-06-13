// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.horizon.starlight.config.EventTypeRoutingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Applies content-based event-type routing at publish time.
 *
 * <p>For an eligible event (routing enabled, matching publisher, original type within the configured
 * prefix), the first matching {@link EventTypeRoutingConfig.RoutingRule} rewrites {@code event.type} in
 * place. Downstream, {@code PublisherService} authorises and publishes the event under the new type, and
 * the multiplexer routes it by type instead of running a content filter.
 *
 * <p>Routing decisions are made once here, at the producer-facing ingress, on the already-deserialised
 * payload ({@code event.getData()}) — never re-parsing it. When nothing matches (or routing is disabled)
 * the event is left untouched and follows the existing flow, so the feature is strictly additive and
 * safe to enable incrementally.
 *
 * @see EventTypeRoutingConfig
 */
@Service
@Slf4j
public class EventTypeRoutingService {

    private final EventTypeRoutingConfig config;

    public EventTypeRoutingService(EventTypeRoutingConfig config) {
        this.config = config;
    }

    /**
     * Rewrites {@code event.type} in place when an enabled routing rule matches.
     *
     * <p>No-op unless routing is enabled, {@code publisherId} matches the configured publisher (when set),
     * and the event's current type starts with the configured applicable prefix (when set). Rules are
     * evaluated in order; the first match wins.
     *
     * @param event       the event being published (mutated in place on a match)
     * @param publisherId the publisher id (OAuth2 {@code clientId}) of the caller
     */
    public void applyRouting(Event event, String publisherId) {
        if (!config.isEnabled() || event == null || event.getType() == null) {
            return;
        }
        if (StringUtils.hasText(config.getPublisherId()) && !config.getPublisherId().equals(publisherId)) {
            return;
        }
        if (StringUtils.hasText(config.getApplicableTypePrefix())
                && !event.getType().startsWith(config.getApplicableTypePrefix())) {
            return;
        }

        for (EventTypeRoutingConfig.RoutingRule rule : config.getRules()) {
            if (matches(event, rule)) {
                var targetType = rule.getTargetType();
                if (StringUtils.hasText(targetType) && !targetType.equals(event.getType())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Routing event id={} from type '{}' to '{}' (publisherId={})",
                                event.getId(), event.getType(), targetType, publisherId);
                    }
                    event.setType(targetType);
                }
                return; // first match wins
            }
        }
    }

    private boolean matches(Event event, EventTypeRoutingConfig.RoutingRule rule) {
        var conditions = rule.getMatch();
        if (conditions == null || conditions.isEmpty()) {
            return false; // never auto-match — avoids an accidental catch-all rule
        }
        for (Map.Entry<String, String> condition : conditions.entrySet()) {
            var actual = extractValue(event.getData(), condition.getKey());
            if (actual == null || !actual.equals(condition.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves a dot-path against {@code event.data} (a deserialised JSON object for Spectre events).
     * Returns {@code null} if the path is absent or resolves to a non-scalar (object/array); only scalar
     * equality is supported.
     */
    @SuppressWarnings("unchecked")
    private String extractValue(Object data, String path) {
        if (!(data instanceof Map) || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = data;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(segment);
            if (current == null) {
                return null;
            }
        }
        if (current instanceof Map || current instanceof Iterable) {
            return null;
        }
        return String.valueOf(current);
    }
}
