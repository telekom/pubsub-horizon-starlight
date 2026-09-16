// Copyright 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.LocalSubscriptionCache;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "starlight.features.localSubscriptionCache", havingValue = "true")
public class LocalSubscriptionCacheInitializer implements ApplicationRunner, HealthIndicator {

    private final LocalSubscriptionCache localSubscriptionCache;
    private final AtomicBoolean initialized = new AtomicBoolean();

    public LocalSubscriptionCacheInitializer(LocalSubscriptionCache localSubscriptionCache) {
        this.localSubscriptionCache = localSubscriptionCache;
    }

    @Override
    public void run(ApplicationArguments args) {
        localSubscriptionCache.prepare();
        localSubscriptionCache.activate();
        initialized.set(true);
    }

    @Override
    public Health health() {
        if (initialized.get()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "Local subscription cache is not initialized").build();
    }
}