// Copyright 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.LocalSubscriptionCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

class LocalSubscriptionCacheInitializerTest {

    @Test
    void shouldPrepareAndActivateCacheBeforeReportingUp() {
        var cache = mock(LocalSubscriptionCache.class);
        var initializer = new LocalSubscriptionCacheInitializer(cache);

        assertEquals(Status.DOWN, initializer.health().getStatus());

        initializer.run(null);

        var ordered = inOrder(cache);
        ordered.verify(cache).prepare();
        ordered.verify(cache).activate();
        assertEquals(Status.UP, initializer.health().getStatus());
    }

    @Test
    void shouldKeepServiceAvailableWhenCacheInitializationFails() {
        var cache = mock(LocalSubscriptionCache.class);
        doThrow(new IllegalStateException("Mongo unavailable")).when(cache).prepare();
        var initializer = new LocalSubscriptionCacheInitializer(cache);

        initializer.run(null);

        assertEquals(Status.DOWN, initializer.health().getStatus());
    }
}