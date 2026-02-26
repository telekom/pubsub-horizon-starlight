// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsConstants;
import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsHelper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.schema.SchemaStore;
import de.telekom.eni.pandora.horizon.tracing.HorizonTracer;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.EventNotCompliantWithSchemaException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.util.Strings;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A service class responsible for managing the schema validation of events.
 */
@Slf4j
@Service
public class SchemaValidationService {

    private final SchemaStore schemaStore;

    private final StarlightConfig starlightConfig;

    private final HorizonMetricsHelper metricsHelper;

    private final HorizonTracer tracer;

    private final ObjectMapper objectMapper;

    @Autowired
    public SchemaValidationService(SchemaStore schemaStore, StarlightConfig starlightConfig, HorizonMetricsHelper metricsHelper, HorizonTracer tracer, ObjectMapper objectMapper) {
        this.schemaStore = schemaStore;
        this.starlightConfig = starlightConfig;
        this.metricsHelper = metricsHelper;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the given event against specific rules and a predefined schema. The rules include:
     * 1. PublisherId must not be null or blank.
     * 2. PublisherId must be formed by <i>hub--team--application</i> and hub and team need to be set.
     * 3. The schema for the event type must be valid.
     * 4. The actual event data must conform to the schema.
     *
     * <p>If any of the validation steps fail, an EventNotCompliantWithSchemaException is thrown.
     *
     * @param event       The Event object to validate.
     * @param environment The environment in which the event is being published.
     * @param publisherId The ID of the publisher (should be <i>hub--team--application</i>)
     * @throws EventNotCompliantWithSchemaException If the event or the publisher ID is not compliant
     *                                              with the validation rules or the schema.
     */
    public void validate(Event event, String environment, String publisherId) throws EventNotCompliantWithSchemaException {
        if (publisherId == null || publisherId.isBlank()){
            log.info("PublisherId is null or blank. Schema validation is canceled because no schema can be clearly assigned.");

            return;
        }

        var splitPubId = publisherId.split("--");
        if (splitPubId.length < 2 || Strings.isBlank(splitPubId[0]) || Strings.isBlank(splitPubId[1])) {
            log.info("Schema validation is canceled because no schema can be clearly assigned to PublisherId {}", publisherId);

            return;
        }

        Schema schema = schemaStore.getSchemaForEventType(environment, event.getType(), splitPubId[0], splitPubId[1]);
        if (schema != null) {
            var currentSpan = Optional.ofNullable(tracer.getCurrentSpan());

            var dataContentType = Optional.ofNullable(event.getDataContentType());
            var mimeType = MimeType.valueOf(dataContentType.orElse(MediaType.APPLICATION_JSON_VALUE));
            if (!mimeType.includes(MediaType.APPLICATION_JSON)) {
                return;
            }

            Object jsonEvent;
            try {
                Object data = event.getData();

                if (data instanceof JSONObject || data instanceof JSONArray) {
                    jsonEvent = data;
                } else if (data instanceof String jsonString) {
                    jsonEvent = jsonString.trim().startsWith("[") ?
                            new JSONArray(jsonString) :
                            new JSONObject(jsonString);
                } else if (data instanceof Map) {
                    jsonEvent = new JSONObject((Map<?, ?>) data);
                } else if (data instanceof Collection) {
                    jsonEvent = new JSONArray((Collection<?>) data);
                } else {
                    String jsonString = objectMapper.writeValueAsString(data);
                    jsonEvent = jsonString.trim().startsWith("[") ?
                            new JSONArray(jsonString) :
                            new JSONObject(jsonString);
                }
            } catch (JsonProcessingException | JSONException e) {
                log.info("Event of type {} is no valid json.", event.getType());

                throw new EventNotCompliantWithSchemaException(String.format("Event of type %s is no valid json.",
                        event.getType()), e);
            }

            try {
                schema.validate(jsonEvent);
                currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(Pair.of("isMatchingSchema", "true"))));
                metricsHelper.getRegistry()
                        .counter(HorizonMetricsConstants.METRIC_SCHEMA_VALIDATION_SUCCESS, "event_type", event.getType(), "publisher_id", publisherId)
                        .increment();

            } catch (ValidationException ex) {
                currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(Pair.of("isMatchingSchema", "false"))));

                log.info("Event of type {} with id {} does not comply with the given schema.",
                        event.getType(), event.getId());

                metricsHelper.getRegistry()
                        .counter(HorizonMetricsConstants.METRIC_SCHEMA_VALIDATION_FAILURE, "event_type", event.getType(), "publisher_id", publisherId)
                        .increment();

                if (!starlightConfig.isEnforceSchemaValidation()) {
                    log.warn("Schema validation is not enforced, skipping compliance check for event of type {} with id {}", event.getType(), event.getId());
                    return;
                }

                throw new EventNotCompliantWithSchemaException(String.format("Event of type %s with id %s does not comply with the given schema.",
                        event.getType(), event.getId()), ex);
            }
        } else {
            log.debug("No spec found for event type {} in environment {}, skipping validation.",
                    event.getType(), environment);
        }
    }
}
