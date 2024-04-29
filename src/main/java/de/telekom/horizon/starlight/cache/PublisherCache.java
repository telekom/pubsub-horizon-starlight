// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.JsonCacheService;
import de.telekom.eni.pandora.horizon.cache.util.Query;
import de.telekom.eni.pandora.horizon.exception.JsonCacheException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.horizon.starlight.config.StarlightConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class PublisherCache {

    private final StarlightConfig starlightConfig;

    private final JsonCacheService<SubscriptionResource> subscriptionCache;

    public PublisherCache(StarlightConfig starlightConfig, JsonCacheService<SubscriptionResource> subscriptionCache) {
        this.starlightConfig = starlightConfig;
        this.subscriptionCache = subscriptionCache;
    }

    public Set<String> findPublisherIds(String environment, String eventType) {
        var env = environment;
        if (Objects.equals(starlightConfig.getDefaultEnvironment(), environment)) {
            env = "default";
        }

        var builder = Query.builder(SubscriptionResource.class)
                .addMatcher("spec.environment", env)
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

            var additionalPublisherIds = a.getSpec().getSubscription().getAdditionalPublisherIds();
            if (additionalPublisherIds != null) {
                publisherIds.addAll(additionalPublisherIds);
            }
        });

        return publisherIds;
    }
}
