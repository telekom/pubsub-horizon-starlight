package de.telekom.horizon.starlight.service.reporting;

import de.telekom.eni.pandora.horizon.model.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(RedisReportingService.class)
@Slf4j
public class NoopReportingService implements ReportingService {


    @Override
    public void markEventProduced(Event event) {}

}
