// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.model.event.PublishedEventMessage;
import de.telekom.eni.pandora.horizon.victorialog.model.AdditionalFields;
import de.telekom.horizon.starlight.cache.PublisherCache;
import de.telekom.horizon.starlight.service.impl.TokenServiceMockImpl;
import de.telekom.horizon.starlight.service.reporting.ReportingService;
import de.telekom.horizon.starlight.test.utils.AbstractIntegrationTest;
import de.telekom.horizon.starlight.test.utils.HorizonTestHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("publisher-mock")
class EventControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PublisherCache publisherCache;

    @SpyBean
    private ReportingService reportingService;

    @BeforeEach
    public void beforeEach() {
        publisherCache.clear();
    }

    @Test
    void testProduceSimpleEvent() throws Exception {
        // given
        Event newEvent = HorizonTestHelper.createNewEvent();

        publisherCache.add("integration", newEvent.getType(), TokenServiceMockImpl.MOCKED_PUBLISHER_ID, Set.of(TokenServiceMockImpl.MOCKED_PUBLISHER_ID));

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
        // given
        Event newEvent = HorizonTestHelper.createNewEvent();

        publisherCache.add("integration", newEvent.getType(), TokenServiceMockImpl.MOCKED_PUBLISHER_ID, Set.of(TokenServiceMockImpl.MOCKED_PUBLISHER_ID));

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

        publisherCache.add("integration", newEvent.getType(), TokenServiceMockImpl.MOCKED_PUBLISHER_ID, Set.of(TokenServiceMockImpl.MOCKED_PUBLISHER_ID));

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

        publisherCache.add("integration", newEvent.getType(), TokenServiceMockImpl.MOCKED_PUBLISHER_ID, Set.of(TokenServiceMockImpl.MOCKED_PUBLISHER_ID));

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
