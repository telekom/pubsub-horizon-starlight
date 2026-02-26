// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.eni.pandora.horizon.kafka.event.EventWriter;
import de.telekom.eni.pandora.horizon.metrics.AdditionalFields;
import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsHelper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.model.event.PublishedEventMessage;
import de.telekom.eni.pandora.horizon.model.event.Status;
import de.telekom.eni.pandora.horizon.tracing.HorizonTracer;
import de.telekom.horizon.starlight.cache.PublisherCache;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.*;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static de.telekom.eni.pandora.horizon.metrics.HorizonMetricsConstants.METRIC_PUBLISHED_EVENTS;

/**
 * A service class responsible for managing the publication of events.
 */
@Service
@Slf4j
public class PublisherService {

    private final PublisherCache publisherCache;

    private final StarlightConfig starlightConfig;

    private final SchemaValidationService schemaValidationService;

    private final HorizonTracer tracer;

    private final HorizonMetricsHelper metricsHelper;

    private final EventWriter eventWriter;

    private final Validator validator;

    private final ObjectMapper objectMapper;


    /**
     * Creates a new PublisherService.
     *
     * @param publisherCache                the publisher cache
     * @param starlightConfig               the configuration for this service
     * @param schemaValidationService       the schema validation service
     * @param tracer                        the tracer used for debug information
     * @param metricsHelper                 the metrics helper for updating metrics
     * @param eventWriter                   the writer for publishing events
     * @param validator                     the validator used for validating the event's fields
     */
    public PublisherService(
            PublisherCache publisherCache,
            StarlightConfig starlightConfig,
            SchemaValidationService schemaValidationService,
            HorizonTracer tracer,
            HorizonMetricsHelper metricsHelper,
            EventWriter eventWriter,
            Validator validator,
            ObjectMapper objectMapper
    ) {
        this.publisherCache = publisherCache;
        this.starlightConfig = starlightConfig;
        this.schemaValidationService = schemaValidationService;
        this.tracer = tracer;
        this.metricsHelper = metricsHelper;
        this.eventWriter = eventWriter;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if the provided realm matches the environment.
     * If the environment equals the default environment, the environment name is set to "default".
     * If the realm does not equal the environment after this, a RealmDoesNotMatchEnvironmentException is thrown.
     *
     * @param realm The realm to check.
     * @param environment The environment to check.
     * @throws RealmDoesNotMatchEnvironmentException if the realm does not match the environment.
     */
    public void checkRealm(String realm, String environment) throws RealmDoesNotMatchEnvironmentException {
        if (Objects.equals(starlightConfig.getDefaultEnvironment(), environment)) {
            environment = "default";
        }

        if (!Objects.equals(realm, environment)) {
            throw new RealmDoesNotMatchEnvironmentException(realm, environment);
        }
    }

    /**
     * Adds the current time to event if it has no time information already.
     * This method checks whether the event already has its time field set and if not
     * it will set the time field to a string representation of the current time in milliseconds.
     *
     * @param event           The event to be published.
     */
    private void addTimeToEventIfAbsent(Event event) {
        if (event != null && event.getTime() == null) {
            var receivedAt = System.currentTimeMillis();
            event.setTime(Instant.ofEpochMilli(receivedAt).toString());
        }
    }

    /**
     * Publishes a specified event.
     * This method processes the event, performs several checks,
     * sends the message to Kafka, and increments the metrics counter.
     * If any errors occur during the processing, the method throws a HorizonStarlightException.
     *
     * @param event           The event to be published.
     * @param publisherId     The ID of the publisher.
     * @param environment     The environment where the event should be published. If null, default is used.
     * @param httpHeaders     The HTTP headers associated with the publishing request. These will be filtered before being attached to the message.
     *
     * @throws HorizonStarlightException If an error occurs while publishing, validating, or handling the event message. Specific exceptions include PayloadTooLargeException and CouldNotPublishEventMessageException. If an InterruptedException happens, it re-interrupts the current thread without throwing the exception.
     *
     */
    public void publish(Event event, String publisherId, String environment,
                        MultiValueMap<String, String> httpHeaders) throws HorizonStarlightException {


        if (starlightConfig.isEnablePublisherCheck()) {
            checkEventTypeOwnership(environment, event.getType(), publisherId);
        }

        if(starlightConfig.isEnableSchemaValidation()) {
            schemaValidationService.validate(event, environment, publisherId);
        }

        addTimeToEventIfAbsent(event);

        var message = new PublishedEventMessage(event, environment);

        message.setStatus(Status.PROCESSED);
        message.setHttpHeaders(filterHttpHeaders(httpHeaders));


        addTrustedStartTimeForObservation(message);

        var span = tracer.startScopedDebugSpan("publish message");
        try {
            tracer.addTagsToSpanFromEventMessage(span, message);
            tracer.addTagsToSpan(span, List.of(Pair.of("publisherId", publisherId)));

            span.annotate("send message to kafka");
            eventWriter.send(starlightConfig.getPublishingTopic(), message, tracer).get();

            span.annotate("export metrics");
            metricsHelper.getRegistry().counter(METRIC_PUBLISHED_EVENTS, metricsHelper.buildTagsFromPublishedEventMessage(message)).increment();
        } catch (Exception e) {
            span.error(e);
            handlePublishException(e);
        } finally {
            span.finish();
        }
    }

    private void handlePublishException(Exception e) throws PayloadTooLargeException, CouldNotPublishEventMessageException {
        if (e instanceof RecordTooLargeException) {
            throw new PayloadTooLargeException("The payload is too large to be published", e);
        } else {
            throw new CouldNotPublishEventMessageException("Failed to publish event", e);
        }
    }

    private void addTrustedStartTimeForObservation(PublishedEventMessage message) {
        if (message.getAdditionalFields() == null) {
            message.setAdditionalFields(new HashMap<>());
        }
        message.getAdditionalFields().put(AdditionalFields.START_TIME_TRUSTED.getValue(), System.currentTimeMillis());
    }

    /**
     * Filters HTTP headers against a predefined blacklist.
     * This method creates a new HttpHeaders object and populates it with
     * the entries from the provided MultiValueMap that do not match any patterns
     * from the header propagation blacklist.
     *
     * @param httpHeaders The HTTP headers to filter. This might be null, in
     *                    which case an empty HttpHeaders object is returned.
     * @return A Map containing the filtered headers. Each entry of the map
     *         consists of a header name and a list of header values.
     */
    private Map<String, List<String>> filterHttpHeaders(MultiValueMap<String, String> httpHeaders) {
        var filteredHeaders = new HttpHeaders();

        if (httpHeaders != null) {
            httpHeaders.forEach((k, v) -> {
                if (starlightConfig.getCompiledHeaderPropagationBlacklist().stream().noneMatch(p -> p.matcher(k).matches())) {
                    var unqiueValues = v.stream().distinct().toList();
                    unqiueValues.forEach(e -> filteredHeaders.add(k, e));
                }
            });
        }


        return filteredHeaders;
    }

    /**
     * Checks event type ownership.
     *
     * @param environment the environment where the event is published
     * @param eventType the type of the event
     * @param publisherId the ID of the publisher
     * @throws PublisherDoesNotMatchEventTypeException if the publisher ID does not match the event type
     * @throws UnknownEventTypeOrNoSubscriptionException if the event type could not be found or there are no subscribers
     */
    private void checkEventTypeOwnership(String environment, String eventType, String publisherId) throws PublisherDoesNotMatchEventTypeException, UnknownEventTypeOrNoSubscriptionException {
        var publisherIds = publisherCache.findPublisherIds(environment, eventType);

        var currentSpan = Optional.ofNullable(tracer.getCurrentSpan());

        if (publisherIds == null || publisherIds.isEmpty()) {
            currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                    Pair.of("isValidEventType", "false")
            )));

            throw new UnknownEventTypeOrNoSubscriptionException(String.format("The event type %s could not be found. It either has not been exposed yet or there are no subscribers'", eventType));
        } else if (StringUtils.isBlank(publisherId) || !publisherIds.contains(publisherId)) {
            currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                    Pair.of("isValidPublisher", "false")
            )));

            throw new PublisherDoesNotMatchEventTypeException(String.format("The event type does not belong to publisher with id '%s'", publisherId));
        }

        currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                Pair.of("isValidEventType", "true"),
                Pair.of("isValidPublisher", "true")
        )));
    }

    /**
     * Validates the event by using a Validator that takes all annotated validation constraints of the model into account.
     * If there are constraint violations the method will add a tag to the current span stating the invalid status of event,
     * before throwing an exceptions that gets passed all violations.
     *
     * @param event The event to be validation.
     *
     * @throws InvalidEventBodyException if event message fails model validation.
     */
    public void validateEvent(Event event) throws InvalidEventBodyException {
        var currentSpan = Optional.ofNullable(tracer.getCurrentSpan());

        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                    Pair.of("isValidEvent", "false")
            )));

            throw new InvalidEventBodyException(violations);
        }

        currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                Pair.of("isValidEvent", "true")
        )));
    }

    /**
     * Checks the size of the payload of the given event.
     * If the event type is in the payload check exemption list, it tags the current span (if it exists)
     * with a key-value pair ("isPayloadTooLarge", "false") and returns.
     * If the payload size is greater than the default max payload size, it throws a PayloadTooLargeException.
     * If the payload size is under the limit, it tags the current span (if it exists) with a
     * key-value pair ("isPayloadTooLarge", "false").
     *
     * @param event the Event object that contains the payload to be checked
     * @throws PayloadTooLargeException if the payload size is greater than the max allowed size
     */
    public void checkPayloadSize(Event event) throws PayloadTooLargeException, InvalidEventBodyException {
        var currentSpan = Optional.ofNullable(tracer.getCurrentSpan());
        if (starlightConfig.getPayloadCheckExemptionList().contains(event.getType())) {
            currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                    Pair.of("exemptedFromPayloadPolicy", "true"),
                    Pair.of("matchesPayloadPolicy", "N/A")
            )));
            return;
        }

        try {
            long payloadSize = objectMapper.writeValueAsString(event.getData()).length();
            if (payloadSize > starlightConfig.getDefaultMaxPayloadSize()) {
                currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                        Pair.of("matchesPayloadPolicy", "false")
                )));

                throw new PayloadTooLargeException("The payload is too large to be published");
            }

            currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                    Pair.of("matchesPayloadPolicy", "true")
            )));
        } catch (JsonProcessingException e) {
            throw new InvalidEventBodyException("Could not serialize event payload");
        }
    }
}
