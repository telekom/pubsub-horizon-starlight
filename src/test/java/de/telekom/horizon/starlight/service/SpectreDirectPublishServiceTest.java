// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsHelper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.horizon.starlight.config.spectre.SpectreDirectPublishConfiguration;
import de.telekom.horizon.starlight.config.spectre.SpectreDirectPublishRule;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class SpectreDirectPublishServiceTest {

    private static final String GENERIC = "de.telekom.ei.listener";
    private static final String DEDICATED =
            "de.telekom.ei.listener.eni--example-team--example-listener";
    private static final String GATEWAY = "gateway";

    private static final String ISSUE = "/eni/example/v1";
    private static final String CONSUMER = "eni--example-consumer--example-app";
    private static final String PROVIDER = "eni--example-provider--example-app";

    private MeterRegistry registry;

    // ---- helpers
    // ---------------------------------------------------------------------------------

    private SpectreDirectPublishConfiguration config(
            boolean enabled, SpectreDirectPublishRule... rules) {
        var c = new SpectreDirectPublishConfiguration();
        c.setEnabled(enabled);
        c.setPublisherId(GATEWAY);
        c.setApplicableType(GENERIC);
        c.setRules(List.of(rules));
        return c;
    }

    /**
     * Builds the service with a fresh {@link SimpleMeterRegistry} captured in {@link #registry}.
     */
    private SpectreDirectPublishService service(SpectreDirectPublishConfiguration cfg) {
        registry = new SimpleMeterRegistry();
        return new SpectreDirectPublishService(cfg, new HorizonMetricsHelper(registry));
    }

    private double rewriteCount(String targetEventType) {
        var counter =
                registry.find(SpectreDirectPublishService.METRIC_DIRECT_PUBLISH)
                        .tag(SpectreDirectPublishService.TAG_TARGET_EVENT_TYPE, targetEventType)
                        .counter();
        return counter == null ? 0d : counter.count();
    }

    private double unmatchedCount(String issue, String consumer, String provider) {
        var counter =
                registry.find(SpectreDirectPublishService.METRIC_DIRECT_PUBLISH_UNMATCHED)
                        .tag(SpectreDirectPublishService.TAG_ISSUE, issue)
                        .tag(SpectreDirectPublishService.TAG_CONSUMER, consumer)
                        .tag(SpectreDirectPublishService.TAG_PROVIDER, provider)
                        .counter();
        return counter == null ? 0d : counter.count();
    }

    private SpectreDirectPublishRule rule(
            String targetEventType, String issue, String consumer, String provider) {
        var r = new SpectreDirectPublishRule();
        r.setTargetEventType(targetEventType);
        r.setIssue(issue);
        r.setConsumer(consumer);
        r.setProvider(provider);
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

    private Map<String, Object> spectreData(String consumer, String provider, String issue) {
        var m = new LinkedHashMap<String, Object>();
        m.put("consumer", consumer);
        m.put("provider", provider);
        m.put("issue", issue);
        m.put("kind", "REQUEST");
        m.put("method", "GET");
        return m;
    }

    // ---- tests
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("rewrites the type on a full issue+consumer+provider match and counts the rewrite")
    void rewritesOnFullMatch() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(DEDICATED, event.getType());
        assertEquals(
                1.0d,
                rewriteCount(DEDICATED),
                "rewrite counter must increment for the target type");
        assertEquals(
                0.0d,
                unmatchedCount(ISSUE, CONSUMER, PROVIDER),
                "a full match must not count as unmatched");
    }

    @Test
    @DisplayName("issue matches but consumer differs → no rewrite, diagnostic counter incremented")
    void issueMatchesConsumerDiffers() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData("other--consumer--app", PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
        assertEquals(
                1.0d,
                unmatchedCount(ISSUE, "other--consumer--app", PROVIDER),
                "the diagnostic counter must carry the event's actual issue/consumer/provider");
    }

    @Test
    @DisplayName("issue matches but provider differs → no rewrite, diagnostic counter incremented")
    void issueMatchesProviderDiffers() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, "other--provider--app", ISSUE));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
        assertEquals(1.0d, unmatchedCount(ISSUE, CONSUMER, "other--provider--app"));
    }

    @Test
    @DisplayName("unrelated issue → no rewrite and NO diagnostic counter")
    void unrelatedIssue() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, PROVIDER, "/eni/other/v1"));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
        assertEquals(
                0.0d,
                unmatchedCount("/eni/other/v1", CONSUMER, PROVIDER),
                "an issue that matches no rule must not be reported as unmatched");
    }

    @Test
    @DisplayName("no-op when the feature is disabled")
    void noopWhenDisabled() {
        var svc = service(config(false, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
    }

    @Test
    @DisplayName("no-op for a publisher other than the configured one")
    void noopForOtherPublisher() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, "some-other-client");

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
    }

    @Test
    @DisplayName("no-op when the caller has no publisher id (null); exact publisher match required")
    void noopForNullPublisher() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, null);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
    }

    @Test
    @DisplayName(
            "the exact applicable-type gate excludes hop-2 re-publishes"
                    + " (de.telekom.ei.listener.<subscriberId>)")
    void gateExcludesRepublishedSubtype() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var republished = listenerEvent(spectreData(CONSUMER, PROVIDER, ISSUE));
        republished.setType(GENERIC + ".eni--other-team--other-listener");

        svc.rewriteTypeForDirectPublish(republished, GATEWAY);

        assertEquals(GENERIC + ".eni--other-team--other-listener", republished.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
    }

    @Test
    @DisplayName("first fully matching rule wins")
    void firstMatchWins() {
        var svc =
                service(
                        config(
                                true,
                                rule("de.telekom.ei.listener.first", ISSUE, CONSUMER, PROVIDER),
                                rule("de.telekom.ei.listener.second", ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(spectreData(CONSUMER, PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals("de.telekom.ei.listener.first", event.getType());
        assertEquals(1.0d, rewriteCount("de.telekom.ei.listener.first"));
        assertEquals(0.0d, rewriteCount("de.telekom.ei.listener.second"));
    }

    @Test
    @DisplayName("null payload data is safe and is a no-op (no counters)")
    void nullDataSafe() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var event = listenerEvent(null);

        assertDoesNotThrow(() -> svc.rewriteTypeForDirectPublish(event, GATEWAY));
        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
    }

    @Test
    @DisplayName(
            "multiple rules share the issue but none fully match → unmatched counter increments"
                    + " once")
    void multipleIssueRulesNoFullMatchCountsOnce() {
        var svc =
                service(
                        config(
                                true,
                                rule("de.telekom.ei.listener.first", ISSUE, CONSUMER, PROVIDER),
                                rule(
                                        "de.telekom.ei.listener.second",
                                        ISSUE,
                                        "another--consumer--app",
                                        PROVIDER)));
        // consumer matches neither rule, but the issue matches both
        var event = listenerEvent(spectreData("unconfigured--consumer--app", PROVIDER, ISSUE));

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount("de.telekom.ei.listener.first"));
        assertEquals(0.0d, rewriteCount("de.telekom.ei.listener.second"));
        assertEquals(
                1.0d,
                unmatchedCount(ISSUE, "unconfigured--consumer--app", PROVIDER),
                "the diagnostic counter must increment exactly once per event, not once per"
                        + " matching-issue rule");
    }

    @Test
    @DisplayName("a nested-object selection field is treated as absent → safe no-op")
    void nestedObjectFieldSafe() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var data = new LinkedHashMap<String, Object>();
        data.put("issue", ISSUE);
        data.put("consumer", CONSUMER);
        data.put("provider", Map.of("nested", "object")); // not a plain value
        var event = listenerEvent(data);

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
        assertEquals(0.0d, unmatchedCount(ISSUE, CONSUMER, PROVIDER));
    }

    @Test
    @DisplayName("data missing issue/consumer/provider is a safe no-op")
    void missingSelectionFieldsSafe() {
        var svc = service(config(true, rule(DEDICATED, ISSUE, CONSUMER, PROVIDER)));
        var incomplete = new LinkedHashMap<String, Object>();
        incomplete.put("issue", ISSUE);
        incomplete.put("consumer", CONSUMER);
        // provider absent
        var event = listenerEvent(incomplete);

        svc.rewriteTypeForDirectPublish(event, GATEWAY);

        assertEquals(GENERIC, event.getType());
        assertEquals(0.0d, rewriteCount(DEDICATED));
        assertEquals(0.0d, unmatchedCount(ISSUE, CONSUMER, PROVIDER));
    }
}
