// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.test.utils;

import com.hazelcast.org.apache.commons.codec.digest.DigestUtils;
import de.telekom.horizon.starlight.config.StarlightConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

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
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private StarlightConfig starlightConfig;

    @Autowired
    public MockMvc mockMvc;

    private static final Map<String, BlockingQueue<ConsumerRecord<String, String>>> multiplexedRecordsMap = new HashMap<>();

    private String eventType;

    @BeforeEach
    void setUp() {
        eventType = "junit.test.event." + DigestUtils.sha1Hex(String.valueOf(System.currentTimeMillis()));

        multiplexedRecordsMap.putIfAbsent(getEventType(), new LinkedBlockingQueue<>());

        var containerProperties = new ContainerProperties(starlightConfig.getPublishingTopic());
        containerProperties.setGroupId("test-starlight-groupid"+getEventType());
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setBeanName("test-consumer-container");
        container.setupMessageListener((MessageListener<String, String>) record -> multiplexedRecordsMap.get(getEventType()).add(record));
        container.start();

        ContainerTestUtils.waitForAssignment(container, broker.getPartitionsPerTopic());
    }

    public ConsumerRecord<String, String> pollForRecord(int timeout, TimeUnit timeUnit) throws InterruptedException {
        return multiplexedRecordsMap.get(getEventType()).poll(timeout, timeUnit);
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
        registry.add("horizon.victorialog.enabled", () -> false);
        registry.add("starlight.security.oauth", () -> false);
        registry.add("starlight.reporting.redis.enabled", () -> false);
        registry.add("spring.data.redis.url", () -> String.format("redis://%s:%d", redisContainer.getHost(), redisContainer.getFirstMappedPort()));
    }

    public String getEventType() {
        return eventType;
    }

}
