// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.health;

import de.telekom.horizon.starlight.cache.PublisherCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "kubernetes.enabled", havingValue = "true")
public class SubscriberCacheHealthIndicator implements HealthIndicator {

    private final PublisherCache publisherCache;

    public SubscriberCacheHealthIndicator(PublisherCache publisherCache) {
        this.publisherCache = publisherCache;
    }

    @Override
    public Health health() {
        Health.Builder status = Health.up();

        if (!publisherCache.isHealthy()) {
            status = Health.down();
        }

        return status.build();
    }
}