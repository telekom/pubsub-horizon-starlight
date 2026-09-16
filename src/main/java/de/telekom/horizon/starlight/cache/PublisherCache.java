// Copyright 2024 Deutsche Telekom IT GmbH
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    private final ObjectProvider<LocalSubscriptionCache> localSubscriptionCacheProvider;

    public PublisherCache(StarlightConfig starlightConfig,
                          JsonCacheService<SubscriptionResource> subscriptionCache,
                          ObjectProvider<LocalSubscriptionCache> localSubscriptionCacheProvider) {
        this.starlightConfig = starlightConfig;
        this.subscriptionCache = subscriptionCache;
        this.localSubscriptionCacheProvider = localSubscriptionCacheProvider;
    }

    public Set<String> findPublisherIds(String environment, String eventType) {
        var env = environment;
        if (Objects.equals(starlightConfig.getDefaultEnvironment(), environment)) {
            env = "default";
        }

        List<SubscriptionResource> list;
        if (starlightConfig.isEnableLocalSubscriptionCache()) {
            var localSubscriptionCache = localSubscriptionCacheProvider.getObject();
            var startedAt = System.nanoTime();
            list = localSubscriptionCache.getByQuery(env, eventType);
            var accessTimeMicros = (System.nanoTime() - startedAt) / 1_000;
            log.debug("Local subscription cache query completed: environment={}, eventType={}, durationMicros={}, matchingEntries={}, totalEntries={}",
                    env, eventType, accessTimeMicros, list.size(), localSubscriptionCache.getEntryCount());
        } else {
            var query = Query.builder(SubscriptionResource.class)
                    .addMatcher("spec.environment", env)
                    .addMatcher("spec.subscription.type", eventType)
                    .build();
            try {
                list = subscriptionCache.getQuery(query);
            } catch (JsonCacheException e) {
                log.error("Error occurred while executing query on JsonCacheService", e);

                throw new SubscriptionMalformedException("A subscription with eventType: " + eventType + " is malformed", e);
            }
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
