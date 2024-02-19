// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PublisherCache {

    //<Environment, EventType> -> Set<PublisherId>
    private final Map<Pair<String, String>, Set<String>> pubCache = new ConcurrentHashMap<>();
    //<Environment, EventType> -> Set<SubscriptionId>
    private final Map<Pair<String, String>, Set<String>> subCache = new ConcurrentHashMap<>();

    public Set<String> get(String environment, String eventType) {
        var key = new ImmutablePair<>(environment, eventType);
        return pubCache.getOrDefault(key, Collections.emptySet());
    }

    public void add(String environment, String eventType, String subscriptionId, Set<String> publisherIds) {
        var key = new ImmutablePair<>(environment, eventType);

        pubCache.put(key, publisherIds);
        subCache.putIfAbsent(key, new HashSet<>());
        subCache.get(key).add(subscriptionId);
    }

    public void remove(String environment, String eventType, String subscriptionId) {
        var key = new ImmutablePair<>(environment, eventType);

        var subscribers = subCache.get(key);

        if(subscribers == null) {
            return;
        }

        subscribers.remove(subscriptionId);

        if(subscribers.isEmpty()) {
            subCache.remove(key);
            pubCache.remove(key);
        }
    }

    public void clear() {
        pubCache.clear();
        subCache.clear();
    }
}
