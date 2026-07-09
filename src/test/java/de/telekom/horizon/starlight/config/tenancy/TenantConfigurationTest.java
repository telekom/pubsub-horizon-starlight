// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config.tenancy;

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

import java.util.HashMap;
import java.util.Map;

class TenantConfigurationTest {

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

    private TenantConfiguration configWith(Map<String, String> rules) {
        var c = new TenantConfiguration();
        c.setEnabled(true);
        c.setRules(rules);
        return c;
    }

    @Test
    @DisplayName("a fully-populated configuration has no violations")
    void fullyPopulatedIsValid() {
        var config = configWith(Map.of("com.example.event", "example-topic"));

        var violations = validator.validate(config);

        assertTrue(violations.isEmpty(), "expected no violations, got: " + violations);
    }

    @Test
    @DisplayName("empty rules map has no violations")
    void emptyRulesIsValid() {
        var config = configWith(Map.of());

        var violations = validator.validate(config);

        assertTrue(violations.isEmpty(), "expected no violations, got: " + violations);
    }

    @Test
    @DisplayName("a blank topic in rules map is a violation")
    void blankTopicValue() {
        var config = configWith(Map.of("com.example.event", "  "));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "blank topic must fail validation");
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("rules")));
    }

    @Test
    @DisplayName("null topic in rules map is a violation")
    void nullTopicValue() {
        var rules = new HashMap<String, String>();
        rules.put("com.example.event", null);
        var config = configWith(rules);

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "null topic must fail validation");
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("rules")));
    }

    @Test
    @DisplayName("multiple invalid topics produce multiple violations")
    void invalidRuleValuesViolationCount() {
        var rules = new HashMap<String, String>();
        rules.put("com.example.event.one", "");
        rules.put("com.example.event.two", "  ");
        var config = configWith(rules);

        var violations = validator.validate(config);

        assertEquals(2, violations.size(), "expected 2 violations, got: " + violations);
    }
}
