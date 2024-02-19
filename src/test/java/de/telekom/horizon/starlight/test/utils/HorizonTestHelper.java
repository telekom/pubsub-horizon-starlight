// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.test.utils;

import de.telekom.eni.pandora.horizon.model.event.Event;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.UUID;

public class HorizonTestHelper {

    public static String TEST_CASE_KEY = "testCase";

    public static Event createNewEvent() {
        var event = new Event();

        event.setId(UUID.randomUUID().toString());
        event.setType("pandora.horizon.starlight.test.caas.v1");
        event.setSpecVersion("v1");
        event.setData(Map.of("foo", "bar", TEST_CASE_KEY, "testProduceSimpleEvent"));
        event.setDataContentType("application/json");
        event.setDataRef("https://data/ref/42");
        event.setSource("https://data/source/42");

        return event;
    }

    public static Event createNewInvalidEvent() {
        var event = new Event();

        // should result in 7 constraint violations
        event.setId("invalid");
        event.setType("invalid_type!");
        event.setTime("invalid");
        event.setSpecVersion("");
        event.setData(Map.of("foo", "bar", TEST_CASE_KEY, "testProduceSimpleEvent")); // data will not be checked actually
        event.setDataContentType("application/json");
        event.setDataRef("  ");
        event.setSource(null);

        return event;
    }

    public static class ResultCaptor<T> implements Answer {
        private T result = null;

        public T getResult() {
            return result;
        }

        @Override
        public T answer(InvocationOnMock invocationOnMock) throws Throwable {
            result = (T) invocationOnMock.callRealMethod();
            return result;
        }
    }
}
