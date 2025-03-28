// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.api;

import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.tracing.HorizonTracer;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.config.rest.WebSecurityConfig;
import de.telekom.horizon.starlight.exception.*;
import de.telekom.horizon.starlight.service.PublisherService;
import de.telekom.horizon.starlight.service.SchemaValidationService;
import de.telekom.horizon.starlight.service.TokenService;
import de.telekom.horizon.starlight.service.impl.TokenServiceMockImpl;
import de.telekom.horizon.starlight.service.reporting.RedisReportingService;
import de.telekom.horizon.starlight.test.utils.HazelcastTestInstance;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static de.telekom.horizon.starlight.test.utils.HorizonTestHelper.createNewEvent;
import static de.telekom.horizon.starlight.test.utils.HorizonTestHelper.createNewInvalidEvent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith({SpringExtension.class, HazelcastTestInstance.class})
@SpringBootTest(properties = {"starlight.features.publisherCheck=true"}, classes = StarlightConfig.class)
@AutoConfigureMockMvc
@EnableWebMvc
@TestPropertySource(properties = {"starlight.security.oauth=false"})
@ContextConfiguration(classes = {WebSecurityConfig.class})
class EventControllerTest {

    private static final String DEFAULT_ENVIRONMENT = "test";
    private static final String TOKEN = "I'm a token";

    @MockBean
    PublisherService publisherService;
    @MockBean
    StarlightConfig starlightConfig;
    @MockBean
    KafkaTemplate<String, String> kafkaTemplate;
    @MockBean
    JwtDecoder jwtDecoder;
    @MockBean
    SchemaValidationService schemaValidationService;
    @MockBean
    TokenService tokenService;
    @MockBean
    HorizonTracer tracer;
    @MockBean
    RestResponseEntityExceptionHandler restResponseEntityExceptionHandler;
    @MockBean
    RedisReportingService redisReportingService;

    @Autowired
    MockMvc mockMvc;

    Validator validator;

    WebTestClient webClient;

    @BeforeEach
    void beforeEach() {
        validator = spy(Validation.buildDefaultValidatorFactory().getValidator());
        webClient = MockMvcWebTestClient.bindToController(new EventController(tokenService, publisherService, tracer, redisReportingService)).controllerAdvice(restResponseEntityExceptionHandler).build();
    }

    @SneakyThrows
    @Test
    @DisplayName("Event can be successfully published via POST API endpoint")
    void eventCanBeSuccessfullyPublished() {
        var publisherId = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;

        when(tokenService.getPublisherId()).thenReturn(publisherId);

        assertDoesNotThrow(() -> {
            doPublishEventRequest(createNewEvent()).expectStatus().isCreated();
            verify(publisherService, times(1)).publish(any(Event.class), eq(publisherId), eq(DEFAULT_ENVIRONMENT),
                    any());
        });
    }

    @SneakyThrows
    @Test
    @DisplayName("Event cannot be published caused by unsuccessful event validation")
    void eventCannotBePublishedDueToUnsuccessfulEventValidation() {
        var publisherId = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;

        var event = createNewInvalidEvent();

        when(tokenService.getPublisherId()).thenReturn(publisherId);

        doThrow(InvalidEventBodyException.class).when(publisherService).validateEvent(any(Event.class));

        assertDoesNotThrow(() -> {
            doPublishEventRequest(event).expectStatus().isBadRequest();
            verify(publisherService, times(0)).publish(any(Event.class), eq(publisherId), eq(DEFAULT_ENVIRONMENT),
                    any());
        });
    }

    @Test
    @DisplayName("Event cannot be published via POST API endpoint due to an error while publishing")
    void eventCannotBePublishedDueToAnErrorWhilePublishing() throws HorizonStarlightException {
        var publisherId = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;

        when(tokenService.getPublisherId()).thenReturn(publisherId);

        doThrow(CouldNotPublishEventMessageException.class).when(publisherService).publish(any(Event.class),
                eq(publisherId), eq(DEFAULT_ENVIRONMENT), any());

        assertDoesNotThrow(() -> {
            doPublishEventRequest(createNewEvent()).expectStatus().is5xxServerError();
            verify(publisherService, times(1)).publish(any(Event.class), eq(publisherId), eq(DEFAULT_ENVIRONMENT),
                    any());
        });
    }

    @Test
    @DisplayName("Event is accepted but not published caused by unknown event type or no subscription")
    void eventCannotBePublishedDueToUnknownEventTypeOrNoSubscription() throws Exception {
        var publisherId = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;

        when(tokenService.getPublisherId()).thenReturn(publisherId);

        doThrow(UnknownEventTypeOrNoSubscriptionException.class).when(publisherService).publish(any(Event.class),
                eq(publisherId), eq(DEFAULT_ENVIRONMENT), any());

        assertDoesNotThrow(() -> {
            doPublishEventRequest(createNewEvent()).expectStatus().isAccepted();
            verify(publisherService, times(1)).publish(any(Event.class), eq(publisherId), eq(DEFAULT_ENVIRONMENT),
                    any());
        });


    }

    @Test
    @DisplayName("Event cannot be published caused by unmatched realm")
    void eventCannotBePublishedDueToRealmDoesNotMatch() throws HorizonStarlightException {
        var publisherId = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;

        when(tokenService.getPublisherId()).thenReturn(publisherId);

        doThrow(RealmDoesNotMatchEnvironmentException.class).when(publisherService).checkRealm(any(), eq(DEFAULT_ENVIRONMENT));

        assertDoesNotThrow(() -> {
            doPublishEventRequest(createNewEvent()).expectStatus().isUnauthorized();
            verify(publisherService, times(0)).publish(any(Event.class), eq(publisherId), eq(DEFAULT_ENVIRONMENT),
                    any());
        });
    }

    @Test
    @DisplayName("Event cannot be published caused by publisher does not own event type")
    void eventCannotBePublishedDueToPublisherNotOwnEventType() throws HorizonStarlightException {
        var publisherId = TokenServiceMockImpl.MOCKED_PUBLISHER_ID;

        when(tokenService.getPublisherId()).thenReturn(publisherId);

        doThrow(PublisherDoesNotMatchEventTypeException.class).when(publisherService).publish(any(Event.class),
                eq(publisherId), eq(DEFAULT_ENVIRONMENT), any());

        assertDoesNotThrow(() -> {
            doPublishEventRequest(createNewEvent()).expectStatus().isForbidden();
            verify(publisherService, times(1)).publish(any(Event.class), eq(publisherId), eq(DEFAULT_ENVIRONMENT),
                    any());
        });
    }

    // helper functions
    private WebTestClient.ResponseSpec doPublishEventRequest(Event event) {
        return webClient.post()
                .uri(String.format("/v1/%s/events", DEFAULT_ENVIRONMENT))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", String.format("Bearer %s", TOKEN))
                .body(BodyInserters.fromValue(event))
                .exchange();
    }
}
