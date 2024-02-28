// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Getter
public class StarlightConfig {

    @Value("${starlight.features.publisherCheck:true}")
    private boolean enablePublisherCheck;

    @Value("${starlight.features.schemaValidation:true}")
    private boolean enableSchemaValidation;

    @Value("#{'${starlight.security.headerPropagationBlacklist}'.split(',')}")
    private List<String> headerPropagationBlacklist;

    @Value("${starlight.defaultEnvironment}")
    private String defaultEnvironment;

    @Value("${starlight.publishingTopic:published}")
    private String publishingTopic;

    @Value("${starlight.publishingTimeout}")
    private Integer starlightTimeout;

    @Value("${starlight.defaultMaxPayloadSize}")
    private Long defaultMaxPayloadSize;

    @Value("#{'${starlight.payloadCheckExemptionList}'.split(',')}")
    private List<String> payloadCheckExemptionList;

}