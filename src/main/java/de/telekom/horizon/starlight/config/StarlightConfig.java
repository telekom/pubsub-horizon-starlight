// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.regex.Pattern;

@Configuration
@Getter
public class StarlightConfig {

    @Value("${starlight.features.publisherCheck:true}")
    private boolean enablePublisherCheck;

    @Value("${starlight.features.schemaValidation:true}")
    private boolean enableSchemaValidation;

    @Value("${starlight.features.enforceSchemaValidation:false}")
    private boolean enforceSchemaValidation;

    @Value("#{'${starlight.security.headerPropagationBlacklist}'.split(',')}")
    private List<String> headerPropagationBlacklist;

    private List<Pattern> compiledHeaderPropagationBlacklist;

    @Value("${starlight.defaultEnvironment}")
    private String defaultEnvironment;

    @Value("${starlight.publishingTopic:published}")
    private String publishingTopic;

    @Value("${starlight.defaultMaxPayloadSize}")
    private Long defaultMaxPayloadSize;

    @Value("#{'${starlight.payloadCheckExemptionList}'.split(',')}")
    private List<String> payloadCheckExemptionList;

    @PostConstruct
    void init() {
        compiledHeaderPropagationBlacklist = headerPropagationBlacklist.stream()
                .map(Pattern::compile)
                .toList();
    }

}