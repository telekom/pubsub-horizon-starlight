// Copyright 2024-2025 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.JsonCacheService;
import de.telekom.eni.pandora.horizon.cache.service.LocalSubscriptionCache;
import de.telekom.eni.pandora.horizon.cache.util.Query;
import de.telekom.eni.pandora.horizon.exception.JsonCacheException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.SubscriptionMalformedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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
    JsonCacheService<SubscriptionResource> subscriptionCache;
    @MockBean
    StarlightConfig starlightConfig;
    @MockBean
    LocalSubscriptionCache localSubscriptionCache;
    @MockBean
    ObjectProvider<LocalSubscriptionCache> localSubscriptionCacheProvider;

    @Test
    void malformedSubscriptionInHazelcastShouldThrowSubscriptionMalformedException() throws JsonCacheException {

        when(subscriptionCache.getQuery(any(Query.class))).thenThrow(new JsonCacheException("subscription is malformed", new RuntimeException()));
        PublisherCache publisherCacheMock = new PublisherCache(starlightConfig, subscriptionCache, localSubscriptionCacheProvider);

        var ex = assertThrows(SubscriptionMalformedException.class, () -> publisherCacheMock.findPublisherIds(DEFAULT_ENVIRONMENT, EVENT_TYPE));
        assertTrue(ex.getMessage().contains(EVENT_TYPE));
        assertInstanceOf(JsonCacheException.class, ex.getCause());
    }

    @Test
    void shouldUseLocalSubscriptionCacheWhenEnabled() throws JsonCacheException {
        var subscription = new SubscriptionResource();
        var spec = new de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResourceSpec();
        var subscriptionDetails = new de.telekom.eni.pandora.horizon.kubernetes.resource.Subscription();
        subscriptionDetails.setPublisherId("publisher");
        subscriptionDetails.setAdditionalPublisherIds(java.util.List.of("additional-publisher"));
        spec.setSubscription(subscriptionDetails);
        subscription.setSpec(spec);
        when(starlightConfig.isEnableLocalSubscriptionCache()).thenReturn(true);
        when(starlightConfig.getDefaultEnvironment()).thenReturn(DEFAULT_ENVIRONMENT);
        when(localSubscriptionCacheProvider.getObject()).thenReturn(localSubscriptionCache);
        when(localSubscriptionCache.getByQuery("default", EVENT_TYPE)).thenReturn(java.util.List.of(subscription));
        var publisherCache = new PublisherCache(starlightConfig, subscriptionCache, localSubscriptionCacheProvider);

        var publisherIds = publisherCache.findPublisherIds(DEFAULT_ENVIRONMENT, EVENT_TYPE);

        assertEquals(java.util.Set.of("publisher", "additional-publisher"), publisherIds);
        verifyNoInteractions(subscriptionCache);
    }
}
