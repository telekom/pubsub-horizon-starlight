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

import java.util.List;

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

    private TenantMapping mapping(List<String> eventTypes, String topic) {
        var m = new TenantMapping();
        m.setEventTypes(eventTypes);
        m.setTopic(topic);
        return m;
    }

    private TenantConfiguration configWith(TenantMapping... rules) {
        var c = new TenantConfiguration();
        c.setEnabled(true);
        c.setRules(List.of(rules));
        return c;
    }

    @Test
    @DisplayName("a fully-populated configuration has no violations")
    void fullyPopulatedIsValid() {
        var config = configWith(mapping(List.of("com.example.event"), "example-topic"));

        var violations = validator.validate(config);

        assertTrue(violations.isEmpty(), "expected no violations, got: " + violations);
    }

    @Test
    @DisplayName("empty event-types on a mapping is a violation")
    void emptyEventTypes() {
        var config = configWith(mapping(List.of(), "example-topic"));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "empty event-types must fail validation");
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("eventTypes")));
    }

    @Test
    @DisplayName("a blank topic on a mapping is a violation")
    void blankTopic() {
        var config = configWith(mapping(List.of("com.example.event"), "  "));

        var violations = validator.validate(config);

        assertFalse(violations.isEmpty(), "blank topic must fail validation");
        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().contains("topic")));
    }

    @Test
    @DisplayName("an incomplete mapping produces violations")
    void incompleteMappingViolationCount() {
        // empty eventTypes and blank topic
        var config = configWith(mapping(List.of(), ""));

        var violations = validator.validate(config);

        // eventTypes has @NotEmpty, topic has @NotBlank
        assertEquals(2, violations.size(), "expected 2 violations, got: " + violations);
    }
}
