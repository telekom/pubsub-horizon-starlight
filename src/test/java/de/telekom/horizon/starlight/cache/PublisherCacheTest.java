package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.cache.service.JsonCacheService;
import de.telekom.eni.pandora.horizon.cache.util.Query;
import de.telekom.eni.pandora.horizon.exception.JsonCacheException;
import de.telekom.eni.pandora.horizon.kubernetes.resource.SubscriptionResource;
import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.horizon.starlight.config.StarlightConfig;
import de.telekom.horizon.starlight.exception.SubscriptionMalformedException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest()
class PublisherCacheTest {

    private static final String DEFAULT_ENVIRONMENT = "test";

    @MockBean
    JsonCacheService<SubscriptionResource> subscriptionCache;
    @MockBean
    StarlightConfig starlightConfig;

    @Test
    void malformedSubscriptionInHazelcastShouldThrowSubscriptionMalformedException() throws JsonCacheException {
        var event = createNewEvent();
        var eventType = event.getType();

        when(subscriptionCache.getQuery(any(Query.class))).thenThrow(new JsonCacheException("subscription is malformed", new Throwable("testing")));
        PublisherCache publisherCacheMock = new PublisherCache(starlightConfig, subscriptionCache);

        assertThrows(SubscriptionMalformedException.class, () -> publisherCacheMock.findPublisherIds(DEFAULT_ENVIRONMENT, eventType));
    }

    private static Event createNewEvent() {
        var event = new Event();
        var eventData = new HashMap<String, String>();

        eventData.put("foo", "bar");
        event.setId(UUID.randomUUID().toString());
        event.setType("pandora.horizon.starlight.test.caas.v1");
        event.setTime(Instant.now().toString());
        event.setSpecVersion("v1");
        event.setData(eventData);
        event.setDataContentType("application/json");
        event.setSource("https://foo/bar/42");

        return event;
    }
}
