package de.telekom.horizon.starlight.api;

import de.telekom.eni.pandora.horizon.model.event.Event;
import de.telekom.eni.pandora.horizon.tracing.HorizonTracer;
import de.telekom.eni.pandora.horizon.victorialog.client.VictoriaLogClient;
import de.telekom.eni.pandora.horizon.victorialog.model.HTTPHeader;
import de.telekom.eni.pandora.horizon.victorialog.model.Observation;
import de.telekom.horizon.starlight.exception.HorizonStarlightException;
import de.telekom.horizon.starlight.service.PublisherService;
import de.telekom.horizon.starlight.service.TokenService;
import de.telekom.horizon.starlight.service.reporting.ReportingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@Slf4j
@RequestMapping("/v1/{environment}")
public class EventController {

    private final TokenService tokenService;

    private final PublisherService publisherService;

    private final HorizonTracer tracer;

    private final VictoriaLogClient victoriaLogClient;

    private final ReportingService reportingService;

    @Autowired
    EventController(TokenService tokenService,
                    PublisherService publisherService,
                    HorizonTracer tracer,
                    VictoriaLogClient victoriaLogClient,
                    ReportingService reportingService) {
        this.tokenService = tokenService;
        this.publisherService = publisherService;
        this.tracer = tracer;
        this.victoriaLogClient = victoriaLogClient;
        this.reportingService = reportingService;
    }

    @RequestMapping(value = { "/events", "/events/" }, method = RequestMethod.HEAD)
    public ResponseEntity<Void> headRequest(@PathVariable String environment) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).header("X-Health-Check-Timestamp", Instant.now().toString()).build();
    }

    @PostMapping(value = { "/events", "/events/" }, consumes = {MediaType.APPLICATION_JSON_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<Event> publishEvent(@RequestBody Event event,
                                              @PathVariable String environment,
                                              @RequestHeader MultiValueMap<String, String> httpHeaders) throws HorizonStarlightException {

        var observation = createObservationFromEvent(event);

        httpHeaders.add(HTTPHeader.TRACK_LATENCY.getValue(), observation.isEmpty() ? "0": "1");

        addTracingTags(event);

        publisherService.checkRealm(tokenService.getRealm(), environment);
        publisherService.validateEvent(event);
        publisherService.checkPayloadSize(event);
        publisherService.publish(event, tokenService.getPublisherId(), environment, httpHeaders);

        reportingService.markEventProduced(event);

        observation.ifPresent(v -> {
            victoriaLogClient.finishAndAddObservation(v);
            victoriaLogClient.countEvent(event);
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    private Optional<Observation> createObservationFromEvent(Event event) {
        Observation observation = null;
        if (victoriaLogClient.shouldStartTrackingLatency()) {
            observation = victoriaLogClient.startObservationFromEvent(event);

        }

        return Optional.ofNullable(observation);
    }

    private void addTracingTags(Event event) {
        var currentSpan = Optional.ofNullable(tracer.getCurrentSpan());

        log.info("currentSpan: {}", tracer.getCurrentSpan());

        currentSpan.ifPresent(s -> tracer.addTagsToSpan(s, List.of(
                Pair.of("eventType", event.getType()),
                Pair.of("eventId", event.getId())
        )));
    }
}
