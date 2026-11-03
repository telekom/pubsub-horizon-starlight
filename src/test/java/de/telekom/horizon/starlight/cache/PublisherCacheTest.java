// Copyright 2024-2025 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.eni.pandora.horizon.subscription.SubscriptionRepository;
import de.telekom.horizon.starlight.config.StarlightConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest()
class PublisherCacheTest {

    private static final String DEFAULT_ENVIRONMENT = "test";
    private static final String EVENT_TYPE = "pandora.horizon.starlight.test.caas.v1";

    @MockBean
    SubscriptionRepository subscriptionCache;
    @MockBean
    StarlightConfig starlightConfig;

    @Test
    void shouldReturnPublisherIdsFromSubscriptionCache() {
        var subscription = new SubscriptionResource();
        var spec = new de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResourceSpec();
        var subscriptionDetails = new de.telekom.eni.pandora.horizon.kubernetes.resource.Subscription();
        subscriptionDetails.setPublisherId("publisher");
        subscriptionDetails.setAdditionalPublisherIds(java.util.List.of("additional-publisher"));
        spec.setSubscription(subscriptionDetails);
        subscription.setSpec(spec);
        when(starlightConfig.getDefaultEnvironment()).thenReturn(DEFAULT_ENVIRONMENT);
        when(subscriptionCache.get(any(), any())).thenReturn(java.util.List.of(subscription));
        var publisherCache = new PublisherCache(starlightConfig, subscriptionCache);

        var publisherIds = publisherCache.findPublisherIds(DEFAULT_ENVIRONMENT, EVENT_TYPE);

        assertEquals(java.util.Set.of("publisher", "additional-publisher"), publisherIds);
    }
}
