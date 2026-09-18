// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.SubscriptionCacheReader;
import de.telekom.eni.pandora.horizon.exception.JsonCacheException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.SubscriptionMalformedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class PublisherCache {

    private final StarlightConfig starlightConfig;

    private final SubscriptionCacheReader subscriptionCache;

    public PublisherCache(StarlightConfig starlightConfig,
                          @Qualifier("subscriptionCacheReader") SubscriptionCacheReader subscriptionCache) {
        this.starlightConfig = starlightConfig;
        this.subscriptionCache = subscriptionCache;
    }

    public Set<String> findPublisherIds(String environment, String eventType) {
        var env = environment;
        if (Objects.equals(starlightConfig.getDefaultEnvironment(), environment)) {
            env = "default";
        }

        List<SubscriptionResource> list;
        try {
            list = subscriptionCache.findByEnvironmentAndEventType(env, eventType);
        } catch (JsonCacheException e) {
            throw new SubscriptionMalformedException("A subscription with eventType: " + eventType + " is malformed", e);
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
