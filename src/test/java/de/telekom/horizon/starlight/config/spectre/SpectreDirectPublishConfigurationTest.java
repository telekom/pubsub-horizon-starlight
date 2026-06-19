// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config.spectre;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class SpectreDirectPublishConfigurationTest {

    private static final String TARGET =
            "de.telekom.ei.listener.eni--example-team--example-listener";
    private static final String ISSUE = "/eni/example/v1";
    private static final String CONSUMER = "eni--example-consumer--example-app";
    private static final String PROVIDER = "eni--example-provider--example-app";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
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

    private SpectreDirectPublishConfiguration configWith(SpectreDirectPublishRule... rules) {
        var c = new SpectreDirectPublishConfiguration();
        c.setEnabled(true);
        c.setApplicableType("de.telekom.ei.listener");
        c.setRules(List.of(rules));
        return c;
    }

    @Test
    @DisplayName("a fully-populated configuration has no violations")
    void fullyPopulatedIsValid() {
        var config = configWith(rule(TARGET, ISSUE, CONSUMER, PROVIDER));

        var violations = validator.validate(config);

        assertTrue(violations.isEmpty(), "expected no violations, got: " + violations);
    }

    @Test
    @DisplayName("a blank applicable-type is a violation")
    void blankApplicableType() {
        var config = configWith(rule(TARGET, ISSUE, CONSUMER, PROVIDER));
        config.setApplicableType("  ");

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "blank applicable-type must fail validation");
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("applicableType")));
    }

    @Test
    @DisplayName("a blank publisher-id is a violation")
    void blankPublisherId() {
        var config = configWith(rule(TARGET, ISSUE, CONSUMER, PROVIDER));
        config.setPublisherId("  ");

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "blank publisher-id must fail validation");
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("publisherId")));
    }

    @Test
    @DisplayName("a blank target-event-type on a rule is a violation")
    void blankTargetEventType() {
        var config = configWith(rule("  ", ISSUE, CONSUMER, PROVIDER));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("targetEventType")));
    }

    @Test
    @DisplayName("a target-event-type violating the Horizon charset is a violation")
    void badCharsetTargetEventType() {
        var config = configWith(rule("not a valid type!", ISSUE, CONSUMER, PROVIDER));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("targetEventType")));
    }

    @Test
    @DisplayName("a blank issue on a rule is a violation")
    void blankIssue() {
        var config = configWith(rule(TARGET, "  ", CONSUMER, PROVIDER));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("issue")));
    }

    @Test
    @DisplayName("a blank consumer on a rule is a violation")
    void blankConsumer() {
        var config = configWith(rule(TARGET, ISSUE, "  ", PROVIDER));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("consumer")));
    }

    @Test
    @DisplayName("a blank provider on a rule is a violation")
    void blankProvider() {
        var config = configWith(rule(TARGET, ISSUE, CONSUMER, "  "));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty());
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("provider")));
    }

    @Test
    @DisplayName("an incomplete rule produces exactly one violation per missing field")
    void incompleteRuleViolationCount() {
        // all four fields blank
        var config = configWith(rule("", "", "", ""));

        var violations = validator.validate(config);

        // targetEventType has @NotBlank + @Pattern (both fire on ""), the other three have
        // @NotBlank
        assertEquals(5, violations.size(), "expected 5 violations, got: " + violations);
    }
}
