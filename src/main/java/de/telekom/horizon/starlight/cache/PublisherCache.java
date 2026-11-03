// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.subscription.SubscriptionRepository;
import de.telekom.horizon.starlight.config.StarlightConfig;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Component
public class PublisherCache {

    private final StarlightConfig starlightConfig;

    private final SubscriptionRepository subscriptionCache;

    public PublisherCache(StarlightConfig starlightConfig, SubscriptionRepository subscriptionRepository) {
        this.starlightConfig = starlightConfig;
        this.subscriptionCache = subscriptionRepository;
    }

    public Set<String> findPublisherIds(String environment, String eventType) {
        var env = environment;
        if (Objects.equals(starlightConfig.getDefaultEnvironment(), environment)) {
            env = "default";
        }

        var list = subscriptionCache.get(env, eventType);

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
