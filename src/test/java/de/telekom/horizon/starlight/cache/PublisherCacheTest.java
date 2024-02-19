// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PublisherCacheTest {

    PublisherCache publisherCache;

    private static final String DEFAULT_ENVIRONMENT = "test";

    private static final String EVENT_TYPE = "pandora.horizon.starlight.test.caas.v1";

    private static final String SUBSCRIPTION_ID = "7369ef5f72b0ad31bd3da3722d2f78e3a0c2ac77";
    private static final String SUBSCRIPTION_ID2 = "7369ad5f72b0ad31bd3da3722d2f78e3a0c2ac23";

    private static final String PUBLISHER_ID = "eni--pandora--horizon-starlight";

    private static final String PUBLISHER_ID2 = "eni--pandora-horizon-starlight2";

    @BeforeEach
    void setup() {
        publisherCache = new PublisherCache();
    }

    @Test
    @DisplayName("Publisher ID can be written to cache and can be read from it again")
    void publisherIdCanBeWrittenAndRead() {
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(empty()));
        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID, Set.of(PUBLISHER_ID));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(equalTo(Set.of(PUBLISHER_ID))));
    }

    @Test
    @DisplayName("Publisher ID can be removed from the cache")
    void publisherIdCanBeRemovedFromCache() {
        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID, Set.of(PUBLISHER_ID));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(notNullValue()));
        publisherCache.remove(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID);
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(empty()));
    }

    @Test
    @DisplayName("Publisher ID cannot be retrieved from cache for non-existing event type")
    void publisherIdIsNullForInvalidEventType() {
        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID, Set.of(PUBLISHER_ID));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(notNullValue()));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, "something.different.v1"), is(empty()));
    }

    @Test
    @DisplayName("Publisher ID cannot be removed for non-existing event type")
    void publisherIdCannotBeRemovedForInvalidEventType() {
        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID, Set.of(PUBLISHER_ID));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(notNullValue()));
        publisherCache.remove(DEFAULT_ENVIRONMENT, "something.different.v1", SUBSCRIPTION_ID);
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(notNullValue()));
    }

    @Test
    @DisplayName("Publisher ID will not be removed if there are still subscribers")
    void publisherIdWillNotBeRemovedWithMultipleSubscribers() {
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(empty()));

        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID, Set.of(PUBLISHER_ID2));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(equalTo(Set.of(PUBLISHER_ID2))));

        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID, Set.of(PUBLISHER_ID));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(equalTo(Set.of(PUBLISHER_ID))));

        publisherCache.add(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID2, Set.of(PUBLISHER_ID));
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(equalTo(Set.of(PUBLISHER_ID))));

        publisherCache.remove(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID);
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(equalTo(Set.of(PUBLISHER_ID))));

        publisherCache.remove(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID);
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(equalTo(Set.of(PUBLISHER_ID))));

        publisherCache.remove(DEFAULT_ENVIRONMENT, EVENT_TYPE, SUBSCRIPTION_ID2);
        assertThat(publisherCache.get(DEFAULT_ENVIRONMENT, EVENT_TYPE), is(empty()));
    }
}
