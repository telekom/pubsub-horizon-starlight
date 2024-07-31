// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.eni.pandora.horizon.metrics.AdditionalFields;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.model.event.PublishedEventMessage;
import de.telekom.horizon.starlight.cache.PublisherCache;
import de.telekom.horizon.starlight.service.TokenService;
import de.telekom.horizon.starlight.service.reporting.ReportingService;
import de.telekom.horizon.starlight.test.utils.AbstractIntegrationTest;
import de.telekom.horizon.starlight.test.utils.HorizonTestHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("publisher-mock")
class EventControllerIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    PublisherCache publisherCache;

    @SpyBean
    TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private ReportingService reportingService;

    private static final String DEFAULT_PUBLISHER = "eni--pandora--foobar";

    @BeforeEach
    void init() {
        when(tokenService.getPublisherId()).thenReturn(DEFAULT_PUBLISHER);
    }

    @Test
    void testProduceSimpleEvent() throws Exception {
        when(publisherCache.findPublisherIds(any(), any())).thenReturn(Set.of(DEFAULT_PUBLISHER));
        // given
        Event newEvent = HorizonTestHelper.createNewEvent();
        // when
        mockMvc.perform(
                post("/v1/integration/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer Foo")
                        .content(objectMapper.writeValueAsString(newEvent))
        ).andExpect(status().isCreated());

        // then
        // we expect a message on kafka
        List<ConsumerRecord<String, String>> receivedList = new LinkedList<>();

        ConsumerRecord<String, String> received;
        while ((received = pollForRecord(3, TimeUnit.SECONDS)) != null) {
            receivedList.add(received);
        }
        assertEquals(1, receivedList.size());
        var publishedEventMessage = objectMapper.readValue(receivedList.getFirst().value(), PublishedEventMessage.class);

        Map<String, Object> receivedAdditionalFields = publishedEventMessage.getAdditionalFields();


        assertTrue(receivedAdditionalFields.containsKey(AdditionalFields.START_TIME_TRUSTED.getValue()));
        assertInstanceOf(Long.class, receivedAdditionalFields.get(AdditionalFields.START_TIME_TRUSTED.getValue()));
    }


    @Test
    void testProduceSimpleEventAndCheckRedisReport() throws Exception {
        when(publisherCache.findPublisherIds(any(), any())).thenReturn(Set.of(DEFAULT_PUBLISHER));

        // given
        Event newEvent = HorizonTestHelper.createNewEvent();

        // when
        mockMvc.perform(
                post("/v1/integration/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer Foo")
                        .content(objectMapper.writeValueAsString(newEvent))
        ).andExpect(status().isCreated());

        // then
        // we expect a message on kafka
        List<ConsumerRecord<String, String>> receivedList = new LinkedList<>();

        ConsumerRecord<String, String> received;
        while ((received = pollForRecord(3, TimeUnit.SECONDS)) != null) {
            receivedList.add(received);
        }
        assertEquals(1, receivedList.size());
        var publishedEventMessage = objectMapper.readValue(receivedList.getFirst().value(), PublishedEventMessage.class);

        Map<String, Object> receivedAdditionalFields = publishedEventMessage.getAdditionalFields();


        assertTrue(receivedAdditionalFields.containsKey(AdditionalFields.START_TIME_TRUSTED.getValue()));
        assertInstanceOf(Long.class, receivedAdditionalFields.get(AdditionalFields.START_TIME_TRUSTED.getValue()));

        verify(reportingService).markEventProduced(any(Event.class));
    }

    @Test
    void testProduceWithoutSubscription() throws Exception {
        when(publisherCache.findPublisherIds(any(), any())).thenReturn(new HashSet<>());

        // given
        Event newEvent = HorizonTestHelper.createNewEvent();

        // when / then
        mockMvc.perform(
                post("/v1/integration/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer Foo")
                        .content(objectMapper.writeValueAsString(newEvent))
        ).andExpect(status().isAccepted());
    }

    @Test
    void testProductInvalidEvent() throws Exception {
        // given
        Event newEvent = HorizonTestHelper.createNewInvalidEvent();

        // when / then
        mockMvc.perform(
                post("/v1/integration/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer Foo")
                        .content(objectMapper.writeValueAsString(newEvent))
        ).andExpect(status().isBadRequest());
    }

    @Test
    void testInvalidReamProduceEvent() throws Exception {
        // given
        Event newEvent = HorizonTestHelper.createNewInvalidEvent();

        // when / then
        mockMvc.perform(
                post("/v1/invalid/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer Foo")
                        .content(objectMapper.writeValueAsString(newEvent))
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void testHealthHeadRequest() throws Exception {
        mockMvc.perform(head("/v1/integration/events"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("X-Health-Check-Timestamp"));
    }

}
