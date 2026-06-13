// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.horizon.starlight.config.EventTypeRoutingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventTypeRoutingServiceTest {

    private static final String GENERIC = "de.telekom.ei.listener";
    private static final String DEDICATED = "de.telekom.ei.dedicated";
    private static final String GATEWAY = "gateway";

    // ---- helpers ---------------------------------------------------------------------------------

    private EventTypeRoutingConfig config(boolean enabled, EventTypeRoutingConfig.RoutingRule... rules) {
        var c = new EventTypeRoutingConfig();
        c.setEnabled(enabled);
        c.setPublisherId(GATEWAY);
        c.setApplicableTypePrefix(GENERIC);
        c.setRules(List.of(rules));
        return c;
    }

    private EventTypeRoutingConfig.RoutingRule rule(String targetType, Map<String, String> match) {
        var r = new EventTypeRoutingConfig.RoutingRule();
        r.setTargetType(targetType);
        r.setMatch(match);
        return r;
    }

    private Event listenerEvent(Object data) {
        var e = new Event();
        e.setId(UUID.randomUUID().toString());
        e.setType(GENERIC);
        e.setSource("https://stargate-integration.test.dhei.telekom.de");
        e.setSpecVersion("1.0");
        e.setDataContentType("application/json");
        e.setData(data);
        return e;
    }

    private Map<String, Object> spectreData(String consumer, String provider, String issue, String kind, String method) {
        var m = new LinkedHashMap<String, Object>();
        m.put("consumer", consumer);
        m.put("provider", provider);
        m.put("issue", issue);
        m.put("kind", kind);
        m.put("method", method);
        return m;
    }

    // ---- tests -----------------------------------------------------------------------------------

    @Test
    @DisplayName("rewrites the type when a rule matches on issue")
    void rewritesOnMatch() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("issue", "fsf-api"))));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, GATEWAY);

        assertEquals(DEDICATED, event.getType());
    }

    @Test
    @DisplayName("no-op when routing is disabled")
    void noopWhenDisabled() {
        var svc = new EventTypeRoutingService(config(false, rule(DEDICATED, Map.of("issue", "fsf-api"))));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
    }

    @Test
    @DisplayName("no-op for a publisher other than the configured one")
    void noopForOtherPublisher() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("issue", "fsf-api"))));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, "some-other-client");

        assertEquals(GENERIC, event.getType());
    }

    @Test
    @DisplayName("no-op when the original type is outside the applicable prefix")
    void noopForNonListenerType() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("issue", "fsf-api"))));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));
        event.setType("accountmanagement.billingaccount.change.attribute.value.v4");

        svc.applyRouting(event, GATEWAY);

        assertEquals("accountmanagement.billingaccount.change.attribute.value.v4", event.getType());
    }

    @Test
    @DisplayName("no-op when no rule matches")
    void noopWhenNoMatch() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("issue", "other-api"))));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
    }

    @Test
    @DisplayName("multi-condition rule requires ALL conditions (AND)")
    void multiConditionAnd() {
        var match = new LinkedHashMap<String, String>();
        match.put("issue", "fsf-api");
        match.put("provider", "fsf-team");
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, match)));

        var partial = listenerEvent(spectreData("c", "other-team", "fsf-api", "REQUEST", "GET"));
        svc.applyRouting(partial, GATEWAY);
        assertEquals(GENERIC, partial.getType(), "should not match when only one condition holds");

        var full = listenerEvent(spectreData("c", "fsf-team", "fsf-api", "REQUEST", "GET"));
        svc.applyRouting(full, GATEWAY);
        assertEquals(DEDICATED, full.getType(), "should match when both conditions hold");
    }

    @Test
    @DisplayName("matches on consumer (consumer is filterable)")
    void matchesOnConsumer() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("consumer", "eni--team--consumer"))));
        var event = listenerEvent(spectreData("eni--team--consumer", "p", "some-api", "REQUEST", "GET"));

        svc.applyRouting(event, GATEWAY);

        assertEquals(DEDICATED, event.getType());
    }

    @Test
    @DisplayName("matches on issue AND consumer together")
    void matchesOnIssueAndConsumer() {
        var match = new LinkedHashMap<String, String>();
        match.put("issue", "some-api");
        match.put("consumer", "eni--team--consumer");
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, match)));

        var wrongConsumer = listenerEvent(spectreData("other--team--consumer", "p", "some-api", "REQUEST", "GET"));
        svc.applyRouting(wrongConsumer, GATEWAY);
        assertEquals(GENERIC, wrongConsumer.getType(), "must not match when the consumer differs");

        var bothMatch = listenerEvent(spectreData("eni--team--consumer", "p", "some-api", "REQUEST", "GET"));
        svc.applyRouting(bothMatch, GATEWAY);
        assertEquals(DEDICATED, bothMatch.getType(), "must match when issue and consumer both hold");
    }

    @Test
    @DisplayName("first matching rule wins")
    void firstMatchWins() {
        var svc = new EventTypeRoutingService(config(true,
                rule("de.telekom.ei.first", Map.of("issue", "fsf-api")),
                rule("de.telekom.ei.second", Map.of("issue", "fsf-api"))));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, GATEWAY);

        assertEquals("de.telekom.ei.first", event.getType());
    }

    @Test
    @DisplayName("matches on a nested header dot-path")
    void matchesNestedPath() {
        var data = spectreData("c", "p", "fsf-api", "REQUEST", "GET");
        data.put("header", Map.of("x-tenant", "premium"));
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("header.x-tenant", "premium"))));
        var event = listenerEvent(data);

        svc.applyRouting(event, GATEWAY);

        assertEquals(DEDICATED, event.getType());
    }

    @Test
    @DisplayName("an empty match never matches (no accidental catch-all)")
    void emptyMatchNeverMatches() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of())));
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
    }

    @Test
    @DisplayName("null payload data is safe and is a no-op")
    void nullDataSafe() {
        var svc = new EventTypeRoutingService(config(true, rule(DEDICATED, Map.of("issue", "fsf-api"))));
        var event = listenerEvent(null);

        assertDoesNotThrow(() -> svc.applyRouting(event, GATEWAY));
        assertEquals(GENERIC, event.getType());
    }

    @Test
    @DisplayName("a blank publisher gate routes regardless of publisher")
    void blankPublisherGateRoutesAnyPublisher() {
        var cfg = config(true, rule(DEDICATED, Map.of("issue", "fsf-api")));
        cfg.setPublisherId("");
        var svc = new EventTypeRoutingService(cfg);
        var event = listenerEvent(spectreData("c", "p", "fsf-api", "REQUEST", "GET"));

        svc.applyRouting(event, "any-client");

        assertEquals(DEDICATED, event.getType());
    }
}
