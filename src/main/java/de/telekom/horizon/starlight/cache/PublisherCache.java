// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.JsonCacheService;
import de.telekom.eni.pandora.horizon.cache.util.Query;
import de.telekom.eni.pandora.horizon.exception.JsonCacheException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class PublisherCache {

    private final JsonCacheService<SubscriptionResource> subscriptionCache;

    public PublisherCache(JsonCacheService<SubscriptionResource> subscriptionCache) {
        this.subscriptionCache = subscriptionCache;
    }

    public Set<String> findPublisherIds(String environment, String eventType) {
        var builder = Query.builder(SubscriptionResource.class)
                .addMatcher("spec.environment", environment)
                .addMatcher("spec.subscription.type", eventType);

        List<SubscriptionResource> list;

        try {
            list = subscriptionCache.getQuery(builder.build());
        } catch (JsonCacheException e) {
            log.error("Error occurred while executing query on JsonCacheService", e);

            return new HashSet<>();
        }

        var publisherIds = new HashSet<String>();

        list.forEach(a -> {
            publisherIds.add(a.getSpec().getSubscription().getPublisherId());
            publisherIds.addAll(a.getSpec().getSubscription().getAdditionalPublisherIds());
        });

        return publisherIds;
    }
}
