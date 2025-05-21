// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service.reporting;

import de.telekom.eni.pandora.horizon.cache.service.JsonCacheService;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.horizon.starlight.test.utils.EmbeddedKafkaHolder;
import de.telekom.horizon.starlight.test.utils.HorizonTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static de.telekom.horizon.starlight.test.utils.HorizonTestHelper.TEST_CASE_KEY;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RedisReportingServiceTest {

    @MockBean
    JsonCacheService<SubscriptionResource> subscriptionCache;

    public static final GenericContainer<?> redisContainer;
    public static final EmbeddedKafkaBroker broker;

    static {
        broker = EmbeddedKafkaHolder.getEmbeddedKafka();
        redisContainer = new GenericContainer<>("redis:6.2.5")
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
        redisContainer.start();
    }

    @Autowired
    private RedisTemplate<String, Integer> redisTemplate;

    @Autowired
    private RedisReportingService redisReportingService;

    @Test
    void markEventProduced() {
        // given
        Event newEvent = HorizonTestHelper.createNewEvent();

        String testCaseValue = "foobarTestValue12";
        newEvent.setData(Map.of(TEST_CASE_KEY, testCaseValue));

        // when
        redisReportingService.markEventProduced(newEvent);

        // then
        assertTrue(redisTemplate.hasKey(testCaseValue));
        Integer storedValue = redisTemplate.opsForValue().get(testCaseValue);
        assertNotNull(storedValue);
        assertEquals(1, storedValue);
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.zipkin.enabled", () -> false);
        registry.add("spring.zipkin.baseUrl", () -> "http://localhost:9411");
        registry.add("horizon.kafka.bootstrapServers", broker::getBrokersAsString);
        registry.add("horizon.kafka.partitionCount", () -> 1);
        registry.add("horizon.kafka.maxPollRecords", () -> 1);
        registry.add("horizon.kafka.autoCreateTopics", () -> true);
        registry.add("horizon.cache.kubernetesServiceDns", () -> "");
        registry.add("kubernetes.enabled", () -> false);
        registry.add("starlight.security.oauth", () -> false);
        registry.add("starlight.reporting.redis.enabled", () -> true);
        registry.add("spring.data.redis.url", () -> String.format("redis://%s:%d", redisContainer.getHost(), redisContainer.getFirstMappedPort()));
    }

}