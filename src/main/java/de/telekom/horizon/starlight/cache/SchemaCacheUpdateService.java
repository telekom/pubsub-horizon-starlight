// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.pandora.horizon.schema.SchemaStore;
import de.telekom.horizon.starlight.config.StarlightConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SchemaCacheUpdateService {

    private final StarlightConfig starlightConfig;

    private final SchemaStore schemaStore;

    @Autowired
    public SchemaCacheUpdateService(SchemaStore schemaStore, StarlightConfig starlightConfig) {
        this.schemaStore = schemaStore;
        this.starlightConfig = starlightConfig;
    }

    @Scheduled(fixedRateString = "${eniapi.refreshInterval}")
    protected void scheduledPollSchemas() {
        if(starlightConfig.isEnableSchemaValidation()) {
            schemaStore.pollSchemas();
        }
    }
}
