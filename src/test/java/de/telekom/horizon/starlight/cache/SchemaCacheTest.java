// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.eniapi.model.EventSpecification;
import de.telekom.eni.eniapiclient.EniApiClient;
import de.telekom.eni.eniapiclient.dto.ItemsWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.everit.json.schema.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaCacheTest {

    @Mock
    EniApiClient eniApiClient;

    @InjectMocks
    SchemaCache schemaCache;

    @Test
    @DisplayName("Initially fill cache when first called")
    void fillCacheWhenFirstCalled() {
        when(eniApiClient.getAllEventSpecifications(anyString())).thenReturn(generateItemWrapper(1));
        Pair<Boolean, Schema> schema = schemaCache.getSchemaForEventType("mock", "foo.bar.v1", "pub", "id");

        assertNotNull(schema.getRight());
        assertTrue(schema.getLeft());
        verify(eniApiClient, times(1)).getAllEventSpecifications(any());
    }

    private ItemsWrapper<EventSpecification> generateItemWrapper(int specificationType) {
        ItemsWrapper<EventSpecification> wrapper = new ItemsWrapper<>();

        List<EventSpecification> specs = new ArrayList<>();

        EventSpecification eventSpecification = new EventSpecification();
        eventSpecification.setType("foo.bar.v1");
        if (specificationType != 3) {
            eventSpecification.setHub("pub");
            eventSpecification.setTeam("id");
        }
        switch (specificationType) {
            //Valid scheme
            case 1 -> eventSpecification.setSpecification(
                    """
                            {
                            "title": "Foo Bar",
                              "type": "object",
                              "properties": {
                                "foo": {
                                  "type": "string",
                                  "description": "Put bar in here"
                                }
                              },
                              "required": ["foo"]
                            }
                            """);

            //Invalid scheme
            case 2 -> eventSpecification.setSpecification(
                    """
                            {
                            "title": "Foo Bar",
                              "type": "object",
                              "properties": {
                                "foo": {
                                  "type": "string",
                                  "description": "Put bar in here"
                                },
                                "required": ["foo"]
                              }
                            }
                            """);

            //Valid scheme, invalid EventSpec
            case 3 -> eventSpecification.setSpecification(
                    """
                            {
                            "title": "Foo Bar",
                              "type": "object",
                              "properties": {
                                "foo": {
                                  "type": "string",
                                  "description": "Put bar in here"
                                }
                              },
                              "required": ["foo"]
                            }
                            """);
        }

        specs.add(eventSpecification);
        wrapper.setItems(specs);
        return wrapper;
    }

    @Test
    @DisplayName("Only call eni api once for multiple requests")
    void onlyCallEniApiOnceForMultipleRequests() {
        when(eniApiClient.getAllEventSpecifications(anyString())).thenReturn(generateItemWrapper(1));
        schemaCache.getSchemaForEventType("mock", "foo.bar.v1", "pub", "id");
        schemaCache.getSchemaForEventType("mock", "foo.bar.v1", "pub", "id");

        verify(eniApiClient, times(1)).getAllEventSpecifications(any());
    }

    @Test
    @DisplayName("Should return null when the schema is invalid")
    void returnNullWhenSchemeIsInvalid() {
        when(eniApiClient.getAllEventSpecifications(anyString())).thenReturn(generateItemWrapper(2));
        Pair<Boolean, Schema> schema = schemaCache.getSchemaForEventType("mock", "foo.bar.v1", "pub", "id");

        assertNull(schema.getRight());
        assertFalse(schema.getLeft());
    }

    @Test
    @DisplayName("Should return null when spec has no scheme")
    void returnNullWhenSpecHasNoScheme() {
        when(eniApiClient.getAllEventSpecifications(anyString())).thenReturn(generateItemWrapper(0));
        Pair<Boolean, Schema> schema = schemaCache.getSchemaForEventType("mock", "foo.bar.v1", "pub", "id");
        
        assertNull(schema);
    }

    @Test
    @DisplayName("Should return null when EventSpec is invalid")
    void returnNullWhenEventSpecIsInvalid() {
        when(eniApiClient.getAllEventSpecifications(anyString())).thenReturn(generateItemWrapper(3));
        Pair<Boolean, Schema> schema = schemaCache.getSchemaForEventType("mock", "foo.bar.v1", "pub", "id");

        assertNull(schema);
    }
}