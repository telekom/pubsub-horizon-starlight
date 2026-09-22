// Copyright 2024-2025 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.SubscriptionCacheReader;
import de.telekom.eni.pandora.horizon.exception.SubscriptionCacheReadException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.SubscriptionCacheAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest()
class PublisherCacheTest {

    private static final String DEFAULT_ENVIRONMENT = "test";
    private static final String EVENT_TYPE = "pandora.horizon.starlight.test.caas.v1";

    @MockBean
    SubscriptionCacheReader subscriptionCache;
    @MockBean
    StarlightConfig starlightConfig;
    @Test
    void cacheReadFailureShouldThrowSubscriptionCacheAccessException() throws SubscriptionCacheReadException {

        when(subscriptionCache.findByEnvironmentAndEventType(any(), any()))
                .thenThrow(new SubscriptionCacheReadException("cache unavailable", new RuntimeException()));
        PublisherCache publisherCacheMock = new PublisherCache(starlightConfig, subscriptionCache);

        var ex = assertThrows(SubscriptionCacheAccessException.class,
                () -> publisherCacheMock.findPublisherIds(DEFAULT_ENVIRONMENT, EVENT_TYPE));
        assertTrue(ex.getMessage().contains(EVENT_TYPE));
        assertInstanceOf(SubscriptionCacheReadException.class, ex.getCause());
    }

    @Test
    void shouldReturnPublisherIdsFromSubscriptionCache() throws SubscriptionCacheReadException {
        var subscription = new SubscriptionResource();
        var spec = new de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResourceSpec();
        var subscriptionDetails = new de.telekom.eni.pandora.horizon.kubernetes.resource.Subscription();
        subscriptionDetails.setPublisherId("publisher");
        subscriptionDetails.setAdditionalPublisherIds(java.util.List.of("additional-publisher"));
        spec.setSubscription(subscriptionDetails);
        subscription.setSpec(spec);
        when(starlightConfig.getDefaultEnvironment()).thenReturn(DEFAULT_ENVIRONMENT);
        when(subscriptionCache.findByEnvironmentAndEventType(any(), any())).thenReturn(java.util.List.of(subscription));
        var publisherCache = new PublisherCache(starlightConfig, subscriptionCache);

        var publisherIds = publisherCache.findPublisherIds(DEFAULT_ENVIRONMENT, EVENT_TYPE);

        assertEquals(java.util.Set.of("publisher", "additional-publisher"), publisherIds);
    }
}
