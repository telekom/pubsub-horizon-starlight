// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.service.reporting;

import de.telekom.eni.pandora.horizon.model.event.Event;

public interface ReportingService {
    void markEventProduced(Event event);
}
