// Copyright 2026 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.config.tenancy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "starlight.tenants")
public class TenantConfiguration {

    /**
     * Master switch. When {@code true} events will be checked against the configured event-type -> topic mapping.
     */
    private boolean enabled = false;

    /**
     * A list of rules that associate event-types with a specific publishing topic.
     */
    private List<TenantMapping> rules = new ArrayList<>();

}
