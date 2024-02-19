// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.cache;

import de.telekom.eni.eniapi.model.EventSpecification;
import lombok.Getter;

import java.util.Objects;

@Getter
public class SchemaCacheKey {

    private final String environment;
    private final String eventType;
    private final String hub;
    private final String team;

    public SchemaCacheKey(String environment, EventSpecification es) {
        this(environment, es.getType(), es.getHub(), es.getTeam());
    }

    public SchemaCacheKey(String environment, String eventType, String hub, String team) {
        this.environment = environment;
        this.eventType = eventType;
        this.hub = hub;
        this.team = team;
    }

    public boolean isValid() {
        return environment != null && eventType != null && hub != null && team != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (getClass() != o.getClass()) {
            return false;
        }

        var key = (SchemaCacheKey) o;

        boolean isEqualEnvironment = Objects.equals(environment, key.environment);
        boolean isEqualEventType = Objects.equals(eventType, key.eventType);
        boolean isEqualHub = Objects.equals(hub, key.hub);
        boolean isEqualTeam = Objects.equals(team, key.team);

        return isEqualEnvironment && isEqualEventType && isEqualHub && isEqualTeam;
    }

    @Override
    public int hashCode() {
        return Objects.hash(environment, eventType, hub, team);
    }
}
