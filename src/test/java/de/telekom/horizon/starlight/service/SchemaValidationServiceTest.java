// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsHelper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.schema.SchemaStore;
import de.telekom.eni.pandora.horizon.tracing.HorizonTracer;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.EventNotCompliantWithSchemaException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Getter;
import lombok.Setter;
import org.everit.json.schema.Schema;
import org.everit.json.schema.SchemaException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaValidationServiceTest {

    public static final String PUB_ID_MOCK = "pub--id--mock";
    public static final String ENV_MOCK = "mock";
    @Mock
    SchemaStore schemaStore;

    @Mock
    HorizonTracer tracer;

    @Mock
    HorizonMetricsHelper metricsHelper;

    @Mock
    StarlightConfig starlightConfig;

    @InjectMocks
    SchemaValidationService schemaValidationService;

    @ParameterizedTest
    @MethodSource("provideParameters")
    @DisplayName("return when event complies with scheme and throws when it does not")
    void isValidTest(int specificationType, boolean withCorrectData, boolean shouldThrow) {
        when(schemaStore.getSchemaForEventType(anyString(), anyString(), anyString(), anyString())).thenReturn(generateSchemes(specificationType));
        Event event = generateEvent(withCorrectData);
        boolean validSchema = specificationType == 1;

        if (shouldThrow) {
            if (validSchema) {
                when(starlightConfig.isEnforceSchemaValidation()).thenReturn(true);
                when(metricsHelper.getRegistry()).thenReturn(new SimpleMeterRegistry());
            }
            assertThrows(EventNotCompliantWithSchemaException.class, () -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
        } else {
            if (validSchema) {
                when(metricsHelper.getRegistry()).thenReturn(new SimpleMeterRegistry());
            }
            assertDoesNotThrow(() -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
        }
    }

    @Test
    @DisplayName("return when schemas are not enforced")
    void shouldReturnWhenSchemasAreNotEnforced() {
        Event event = generateEvent(true);
        when(schemaStore.getSchemaForEventType(anyString(), anyString(), anyString(), anyString())).thenReturn(generateSchemes(1));

        assertDoesNotThrow(() -> {
            when(metricsHelper.getRegistry()).thenReturn(new SimpleMeterRegistry());
            schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK);
        });
    }

    @Test
    @DisplayName("return when no specification is given")
    void shouldReturnWhenNoSpecIsGiven() {
        when(schemaStore.getSchemaForEventType(anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        Event event = generateEvent(true);

        assertDoesNotThrow(() -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
    }

    @Test
    @DisplayName("throw when event data isn't valid json but the dataContentType suggests otherwise")
    void shouldReturnWhenEventDataIsNoValidJson() {
        when(schemaStore.getSchemaForEventType(anyString(), anyString(), anyString(), anyString())).thenReturn(generateSchemes(1));

        // Validate an event with data that is valid json but uses a json suffix in its dataContentType
        {
            Event event = generateEvent(true);
            event.setDataContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            assertDoesNotThrow(() -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
        }

        // Validate an event with data that is not valid JSON despite the dataContentType claiming it is.
        {
            Event event = generateEvent(true);
            event.setDataContentType(MediaType.APPLICATION_JSON_VALUE);
            event.setData("<optional JSON scheme>");

            assertThrows(EventNotCompliantWithSchemaException.class, () -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
        }

        // Validate an event with data that is not valid JSON but without specifically setting dataContentType.
        {
            Event event = generateEvent(true);
            event.setData("<optional JSON scheme>");

            assertThrows(EventNotCompliantWithSchemaException.class, () -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
        }

        // Validate an event with data that is not valid JSON but the dataContentType defines it as non-JSON
        {
            Event event = generateEvent(true);
            event.setDataContentType("text/plain");
            event.setData("This is not a valid JSON string as suggested by the dataContentType");

            assertDoesNotThrow(() -> schemaValidationService.validate(event, ENV_MOCK, PUB_ID_MOCK));
        }
    }

    @Test
    @DisplayName("validate should not throw when publisherID is malformed")
    void shouldNotThrowWhenPublisherIdIsMalformed() {
        Event event = generateEvent(true);
        // Note: PublisherId "gateway" is a special publisherId that used by CloudWalker and Spectre
        // We currently cannot safely assign a schema to it, but we should not throw an exception in this case
        assertDoesNotThrow(() -> schemaValidationService.validate(event, "mock", "gateway"));

        verify(schemaStore, times(0)).getSchemaForEventType(anyString(), anyString(), anyString(), anyString());
    }

    private Schema generateSchemes(int specificationType) {
        String spec = "";
        switch (specificationType) {
            case 1:
                spec =
                        """
                                {
                                "title": "Foo Bar",
                                  "type": "object",
                                  "properties": {
                                    "foo": {
                                      "type": "string",
                                      "description": "Put bar in here"
                                    }
                                  },
                                  "required": ["foo"]
                                }
                                """;
                break;
            //Invalid scheme
            case 2:
                spec =
                        """
                                {
                                "title": "Foo Bar",
                                  "type": "object",
                                  "properties": {
                                    "foo": {
                                      "type": "string",
                                      "description": "Put bar in here"
                                    },
                                    "required": ["foo"]
                                  }
                                }
                                """;
                break;
            default:
                return null;
        }
        Schema schema;
        try {
            schema = getSchemaFromString(spec);
            return schema;
        } catch (SchemaException ex) {
            return null;
        }
    }

    private Event generateEvent(boolean withCorrectData) {


        Event event = new Event();
        event.setType("foo.bar.v1");

        if (withCorrectData) {
            CorrectDummyData dummyData = new CorrectDummyData();
            dummyData.setFoo("bar");

            event.setData(dummyData);
        } else {
            WrongDummyData dummyData = new WrongDummyData();
            dummyData.setBar("foo");

            event.setData(dummyData);
        }

        return event;
    }

    private Schema getSchemaFromString(String input) throws SchemaException {
        if (input != null && !input.isEmpty()) {
            JSONObject jsonSchema = new JSONObject(input);
            return SchemaLoader.load(jsonSchema);
        } else {
            return null;
        }
    }

    @Getter
    @Setter
    static class CorrectDummyData {
        String foo;
    }

    @Getter
    @Setter
    static class WrongDummyData {
        String bar;
    }

    private static Stream<Arguments> provideParameters() {
        return Stream.of(
                arguments(1, true, false),
                arguments(1, false, true),
                arguments(2, true, false),
                arguments(0, true, false)
        );
    }

}
