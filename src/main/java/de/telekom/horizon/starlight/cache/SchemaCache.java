package de.telekom.horizon.starlight.cache;

import de.telekom.eni.eniapi.model.EventSpecification;
import de.telekom.eni.eniapiclient.EniApiClient;
import de.telekom.eni.eniapiclient.dto.ItemsWrapper;
import de.telekom.horizon.starlight.config.StarlightConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.everit.json.schema.Schema;
import org.everit.json.schema.SchemaException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SchemaCache {

    // SchemaCacheKey -> <isValid, Schema>
    private final ConcurrentHashMap<SchemaCacheKey, Pair<Boolean, Schema>> schemaMap = new ConcurrentHashMap<>();
    private final List<String> polledEnvironments = new ArrayList<>();
    private final EniApiClient eniApiClient;

    private final StarlightConfig starlightConfig;

    @Autowired
    public SchemaCache(EniApiClient eniApiClient, StarlightConfig starlightConfig) {
        this.eniApiClient = eniApiClient;
        this.starlightConfig = starlightConfig;
    }

    public Pair<Boolean, Schema> getSchemaForEventType(String environment, String eventType, String hub, String team) {
        var sck = new SchemaCacheKey(environment, eventType, hub, team);

        if (!polledEnvironments.contains(environment)) {
            try {
                pollSchemas(environment);
            } catch (Exception e) {
                log.warn("Could not reach ENI-api, skipping schema-validation for event-type {} in environment {}", eventType, environment);
                log.debug(e.getMessage(), e);
                return null;
            }
        }

        return schemaMap.getOrDefault(sck, null);
    }

    private void processEventSpecification(String environment, EventSpecification spec) {
        var sck = new SchemaCacheKey(environment, spec);
        if (!sck.isValid()) {
            log.info("EventSpecification for eventType {} is not valid, it will not be cached.", spec.getType());
            return;
        }

        try {
            var schema = getSchemaFromString(spec.getSpecification());
            if (schema != null) {
                schemaMap.put(sck, new ImmutablePair<>(true, schema));
            } else {
                schemaMap.remove(sck);
            }
        } catch (SchemaException | JSONException ex) {
            log.debug("Schema for eventType {} is not valid, it will not be cached.", spec.getType(), ex);
            schemaMap.put(sck, new ImmutablePair<>(false, null));
        }
    }

    private void pollSchemas(String environment) {
        ItemsWrapper<EventSpecification> specsWrapper = eniApiClient.getAllEventSpecifications(environment);
        if (specsWrapper != null && specsWrapper.getItems() != null) {
            List<EventSpecification> eventSpecifications = specsWrapper.getItems();
            log.debug("Polled {} EventSpecifications.", eventSpecifications.size());

            eventSpecifications.forEach(spec -> processEventSpecification(environment, spec));
        }

        //Add environment to list of polled environments so that schemas will be polled periodically
        if (!polledEnvironments.contains(environment)) {
            polledEnvironments.add(environment);
        }
    }

    private Schema getSchemaFromString(String input) throws SchemaException, JSONException {
        if (input != null && !input.isEmpty()) {
            var jsonSchema = new JSONObject(input);
            var sb = SchemaLoader.builder()
                    .schemaJson(jsonSchema)
                    .draftV7Support()
                    .build();

            return sb.load().build();
        } else {
            return null;
        }
    }

    @Scheduled(fixedRateString = "${eniapi.refreshInterval}")
    protected void scheduledPollSchemas() {
        if(starlightConfig.isEnableSchemaValidation()) {
            polledEnvironments.stream().distinct().forEach(this::pollSchemas);
        }
    }
}
