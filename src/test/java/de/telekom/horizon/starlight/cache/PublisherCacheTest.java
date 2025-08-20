package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.JsonCacheService;
import de.telekom.eni.pandora.horizon.cache.util.Query;
import de.telekom.eni.pandora.horizon.exception.JsonCacheException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.SubscriptionMalformedException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest()
class PublisherCacheTest {

    private static final String DEFAULT_ENVIRONMENT = "test";
    private static final String EVENT_TYPE = "pandora.horizon.starlight.test.caas.v1";

    @MockBean
    JsonCacheService<SubscriptionResource> subscriptionCache;
    @MockBean
    StarlightConfig starlightConfig;

    @Test
    void malformedSubscriptionInHazelcastShouldThrowSubscriptionMalformedException() throws JsonCacheException {

        when(subscriptionCache.getQuery(any(Query.class))).thenThrow(new JsonCacheException("subscription is malformed", new RuntimeException()));
        PublisherCache publisherCacheMock = new PublisherCache(starlightConfig, subscriptionCache);

        var ex = assertThrows(SubscriptionMalformedException.class, () -> publisherCacheMock.findPublisherIds(DEFAULT_ENVIRONMENT, EVENT_TYPE));
        assertTrue(ex.getMessage().contains(EVENT_TYPE));
        assertInstanceOf(JsonCacheException.class, ex.getCause());
    }
}
