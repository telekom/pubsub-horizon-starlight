// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service.reporting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.telekom.eni.pandora.horizon.model.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(value = "starlight.reporting.redis.enabled", havingValue = "true")
@Slf4j
public class RedisReportingService implements ReportingService {

    private final ObjectMapper objectMapper;

    private final RedisTemplate<String, Integer> redisTemplate;

    @Autowired
    public RedisReportingService(ObjectMapper objectMapper,
                                 RedisTemplate<String, Integer> redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void markEventProduced(Event event) {
        try {
            var testCaseName = getTestCaseValueFromEvent(event);
            redisTemplate.opsForValue().increment(testCaseName);
        } catch (Exception e) {
            log.error("Cannot increment redis key for load test reporting", e);
        }
    }

    private String getTestCaseValueFromEvent(Event event) {
        Map<String, String> eventData = objectMapper.convertValue(event.getData(), new TypeReference<>() {});
        return eventData.get("testCase");
    }


}
