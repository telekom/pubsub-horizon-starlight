// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service;

import brave.ScopedSpan;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.eni.pandora.horizon.autoconfigure.kafka.KafkaAutoConfiguration;
import de.telekom.eni.pandora.horizon.kafka.config.KafkaProperties;
import de.telekom.eni.pandora.horizon.kubernetes.SubscriptionResourceListener;
import de.telekom.eni.pandora.horizon.metrics.HorizonMetricsHelper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.model.event.PublishedEventMessage;
import de.telekom.eni.pandora.horizon.tracing.HorizonTracer;
import de.telekom.eni.pandora.horizon.tracing.ScopedDebugSpanWrapper;
import de.telekom.horizon.starlight.cache.PublisherCache;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.*;
import de.telekom.horizon.starlight.service.impl.TokenServiceMockImpl;
import de.telekom.horizon.starlight.test.utils.HorizonTestHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static de.telekom.horizon.starlight.test.utils.HorizonTestHelper.createNewInvalidEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {PublisherService.class, KafkaAutoConfiguration.class, LocalValidatorFactoryBean.class})
class PublisherServiceTest {

    private static final String DEFAULT_ENVIRONMENT = "test";
    private static final String DEFAULT_PUBLISHER_ID = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;
    private static final String DEFAULT_TOPIC = "published";

    @MockBean
    SubscriptionResourceListener subscriptionResourceListener;
    @MockBean(name = "kafkaTemplate")
    KafkaTemplate<String, String> kafkaTemplate;
    @MockBean
    PublisherCache publisherCache;
    @MockBean
    StarlightConfig starlightConfig;
    @MockBean
    SchemaValidationService schemaValidationService;
    @MockBean
    HorizonTracer tracer;
    @Mock
    ScopedDebugSpanWrapper scopedDebugSpanWrapper;
    @MockBean
    HorizonMetricsHelper metricsHelper;
    @Autowired
    PublisherService publisherService;
    @Autowired
    KafkaProperties kafkaProperties;
    @SpyBean
    Validator validator;

    @Test
    void publisherServiceCanBeInitialized() {
        assertThat(publisherService, notNullValue());
    }

    @Test
    void verifySubscriptionResourceListenerIsStarted() {
        publisherService.init();
        verify(subscriptionResourceListener, times(1)).start();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Event can be published successfully")
    void eventMessageCanBePublishedSuccessfully(boolean isEnablePublisherCheck) {
        var offset = 0L;
        var partition = 0;

        var event = createNewEvent();
        var message = new PublishedEventMessage(event, DEFAULT_ENVIRONMENT);

        when(starlightConfig.isEnablePublisherCheck()).thenReturn(isEnablePublisherCheck);
        when(starlightConfig.getPublishingTopic()).thenReturn("published");
        when(publisherCache.get(DEFAULT_ENVIRONMENT, event.getType())).thenReturn(Set.of(DEFAULT_PUBLISHER_ID));
        when(tracer.startScopedDebugSpan(anyString())).thenReturn(scopedDebugSpanWrapper);

        var counterMock = Mockito.mock(Counter.class);
        var registryMock = Mockito.mock(MeterRegistry.class);

        doNothing().when(counterMock).increment();
        when(registryMock.counter(any(), any(Tags.class))).thenReturn(counterMock);

        when(metricsHelper.buildTagsFromPublishedEventMessage(any())).thenReturn(Tags.empty());
        when(metricsHelper.getRegistry()).thenReturn(registryMock);

        assertDoesNotThrow(() -> {
            applyKafkaStubs(DEFAULT_TOPIC, offset, partition, message);
            publisherService.publish(event, DEFAULT_PUBLISHER_ID, DEFAULT_ENVIRONMENT, null);
        });

        verify(publisherCache, times(isEnablePublisherCheck ? 1 : 0)).get(DEFAULT_ENVIRONMENT, message.getEvent().getType());
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }
    @Test
    @DisplayName("Event cannot be published due to a publisherId mismatch or an empty publisherId")
    void eventMessageCanBePublishedWithInvalidPublisherIdWhenCheckIsDisabled() {
        eventMessageCannotBePublishedDueToAPublisherIdMismatch(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"an--invalid--publisherId", ""})
    @DisplayName("Event cannot be published due to a publisherId mismatch or an empty publisherId")
    void eventMessageCannotBePublishedDueToAPublisherIdMismatch(String publisherId) {
        var offset = 0L;
        var partition = 0;

        var event = createNewEvent();
        var message = new PublishedEventMessage(event, DEFAULT_ENVIRONMENT);

        when(starlightConfig.isEnablePublisherCheck()).thenReturn(true);
        when(publisherCache.get(DEFAULT_ENVIRONMENT, message.getEvent().getType())).thenReturn(Set.of(DEFAULT_PUBLISHER_ID));

        assertThrows(PublisherDoesNotMatchEventTypeException.class, () -> {
            applyKafkaStubs(DEFAULT_TOPIC, offset, partition, message);
            publisherService.publish(event, publisherId, DEFAULT_ENVIRONMENT, null);
        });

        verify(kafkaTemplate, times(0)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Event cannot be published due to unknown event type or no subscriptions")
    void eventMessageCannotBePublishedDueToUnknownEventTypeOrNoSubscriptions() {
        var offset = 0L;
        var partition = 0;

        var event = createNewEvent();
        var message = new PublishedEventMessage(event, DEFAULT_ENVIRONMENT);

        when(starlightConfig.isEnablePublisherCheck()).thenReturn(true);
        when(publisherCache.get(DEFAULT_ENVIRONMENT, message.getEvent().getType())).thenReturn(null);

        assertThrows(UnknownEventTypeOrNoSubscriptionException.class, () -> {
            applyKafkaStubs(DEFAULT_TOPIC, offset, partition, message);
            publisherService.publish(event, DEFAULT_PUBLISHER_ID, DEFAULT_ENVIRONMENT, null);
        });

        verify(kafkaTemplate, times(0)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("Event cannot be published due to unknown event type or no subscriptions")
    void eventMessageCannotBePublishedDueToCouldNotPublishEventMessageException() {
        var offset = 0L;
        var partition = 0;

        var event = createNewEvent();
        var message = new PublishedEventMessage(event, DEFAULT_ENVIRONMENT);

        when(starlightConfig.isEnablePublisherCheck()).thenReturn(true);
        when(starlightConfig.getPublishingTopic()).thenReturn("published");
        when(publisherCache.get(DEFAULT_ENVIRONMENT, event.getType())).thenReturn(Set.of(DEFAULT_PUBLISHER_ID));
        when(tracer.startScopedDebugSpan(anyString())).thenReturn(scopedDebugSpanWrapper);

        var counterMock = Mockito.mock(Counter.class);
        var registryMock = Mockito.mock(MeterRegistry.class);

        doNothing().when(counterMock).increment();
        when(registryMock.counter(any(), any(Tags.class))).thenReturn(counterMock);

        when(metricsHelper.buildTagsFromPublishedEventMessage(any())).thenReturn(Tags.empty());
        when(metricsHelper.getRegistry()).thenReturn(registryMock);

        assertThrows(CouldNotPublishEventMessageException.class, () -> {
            applyKafkaStubs(DEFAULT_TOPIC, offset, partition, message);

            var responseFuture = mock(CompletableFuture.class);
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(responseFuture);
            when(responseFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException("I timed out writing to kafka"));

            publisherService.publish(event, DEFAULT_PUBLISHER_ID, DEFAULT_ENVIRONMENT, null);
        });
    }

    @Test
    @DisplayName("Event message passes the validation")
    void eventMessagePassesValidation() {
        var event = createNewEvent();

        assertDoesNotThrow(() -> publisherService.validateEvent(event));
    }

    @Test
    @DisplayName("Event does not pass the validation due to constraint violations")
    void eventMessageDoesNotPassValidation() {
        var event = createNewInvalidEvent();

        HorizonTestHelper.ResultCaptor<Set<ConstraintViolation>> resultCaptor = new HorizonTestHelper.ResultCaptor<>();
        doAnswer(resultCaptor).when(validator).validate(any(Event.class));

        assertThrows(InvalidEventBodyException.class, () -> publisherService.validateEvent(event));

        assertThat(resultCaptor.getResult(), is(notNullValue()));
        resultCaptor.getResult().forEach(v -> System.out.println(v.getMessage()));
        assertThat(resultCaptor.getResult().size(), is(7));
    }

    @Test
    @DisplayName("Too large payload should throw an exception")
    void tooLargePayloadShouldThrowAnException() {
        when(starlightConfig.getDefaultMaxPayloadSize()).thenReturn(1L);

        var event = createNewEvent();
        event.setData(Map.of("foo", "bar", "fizz", "buzz"));

        assertThrows(PayloadTooLargeException.class, () -> publisherService.checkPayloadSize(event), "The payload is too large to be published");
    }

    @Test
    @DisplayName("Too large payload should not throw exception when event-type in exemption list")
    void tooLargePayloadShouldNotThrowAnExceptionWhenEventTypeInExemptionList() {
        when(starlightConfig.getDefaultMaxPayloadSize()).thenReturn(1L);
        when(starlightConfig.getPayloadCheckExemptionList()).thenReturn(List.of("pandora.horizon.starlight.test.caas.v1"));

        var event = createNewEvent();
        event.setData(Map.of("foo", "bar", "fizz", "buzz"));

        assertDoesNotThrow(() -> publisherService.checkPayloadSize(event));
    }

    private static Event createNewEvent() {
        var event = new Event();
        var eventData = new HashMap<String, String>();

        eventData.put("foo", "bar");
        event.setId(UUID.randomUUID().toString());
        event.setType("pandora.horizon.starlight.test.caas.v1");
        event.setTime(Instant.now().toString());
        event.setSpecVersion("v1");
        event.setData(eventData);
        event.setDataContentType("application/json");
        event.setSource("https://foo/bar/42");

        return event;
    }

    private void applyKafkaStubs(String topic, long offset, int partition, PublishedEventMessage message) throws Exception {
        var sendResult = mock(SendResult.class);
        var recordMetadata = mock(RecordMetadata.class);
        var producerRecord = mock(ProducerRecord.class);
        var responseFuture = mock(CompletableFuture.class);

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(responseFuture);
        when(responseFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(sendResult);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(sendResult.getProducerRecord()).thenReturn(producerRecord);
        when(recordMetadata.offset()).thenReturn(offset);
        when(recordMetadata.partition()).thenReturn(partition);
        when(recordMetadata.topic()).thenReturn(topic);
        when(producerRecord.value()).thenReturn(new ObjectMapper().writeValueAsString(message));

        // Kafka tracing stubs
        var scopedSpan = Mockito.mock(ScopedSpan.class);

        when(tracer.startScopedSpan(anyString())).thenReturn(scopedSpan);
        when(tracer.getCurrentTraceId()).thenReturn("foo");
        when(tracer.getCurrentSpanId()).thenReturn("bar");
    }
}
