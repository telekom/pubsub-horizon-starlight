// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.kubernetes;

import de.telekom.eni.pandora.horizon.kubernetes.HorizonResourceEventHandler;
import de.telekom.eni.pandora.horizon.kubernetes.resource.Subscription;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.horizon.starlight.cache.PublisherCache;
import de.telekom.horizon.starlight.config.StarlightConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(value = "kubernetes.enabled", havingValue = "true")
public class SubscriptionResourceEventHandler implements HorizonResourceEventHandler<SubscriptionResource> {

    private final PublisherCache publisherCache;

    private final StarlightConfig starlightConfig;

    public SubscriptionResourceEventHandler(PublisherCache publisherCache, StarlightConfig starlightConfig) {
        this.publisherCache = publisherCache;
        this.starlightConfig = starlightConfig;
    }

    @Override
    public void onAdd(SubscriptionResource resource) {
        log.debug("Add: {}", resource);

        var environment = determineEnvironment(resource).orElse(starlightConfig.getDefaultEnvironment());
        var subscription = resource.getSpec().getSubscription();

        publisherCache.add(environment, subscription.getType(), subscription.getSubscriptionId(), getAllPublisherIdsFromSubscription(subscription));
    }

    @Override
    public void onUpdate(SubscriptionResource oldResource, SubscriptionResource newResource) {
        log.debug("Update: {}", newResource);

        var environment = determineEnvironment(newResource).orElse(starlightConfig.getDefaultEnvironment());
        var subscription = newResource.getSpec().getSubscription();

        publisherCache.add(environment, subscription.getType(), subscription.getSubscriptionId(), getAllPublisherIdsFromSubscription(subscription));
    }

    @Override
    public void onDelete(SubscriptionResource resource, boolean deletedFinalStateUnknown) {
        log.debug("Delete: {}", resource);

        var environment = determineEnvironment(resource).orElse(starlightConfig.getDefaultEnvironment());
        var subscription = resource.getSpec().getSubscription();

        publisherCache.remove(environment, subscription.getType(), subscription.getSubscriptionId());
    }

    private Set<String> getAllPublisherIdsFromSubscription (Subscription subscription) {
        var listOfPublisherIds = Optional.ofNullable(subscription.getAdditionalPublisherIds()).orElse(new ArrayList<>());
        listOfPublisherIds.add(subscription.getPublisherId());

        return listOfPublisherIds.stream().map(String::toLowerCase).collect(Collectors.toSet());
    }

    private Optional<String> determineEnvironment(SubscriptionResource resource) {
        return Optional.ofNullable(resource.getSpec().getEnvironment());
    }

    @Override
    public void onInitialStateSet(Collection<SubscriptionResource> collection) {
        collection.forEach(l -> onAdd(l));
    }
}
