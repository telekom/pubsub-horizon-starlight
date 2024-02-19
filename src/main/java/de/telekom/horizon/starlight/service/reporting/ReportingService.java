package de.telekom.horizon.starlight.service.reporting;

import de.telekom.eni.pandora.horizon.model.event.Event;

public interface ReportingService {
    void markEventProduced(Event event);
}
