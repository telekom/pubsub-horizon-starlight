package de.telekom.horizon.starlight.exception;

import de.telekom.eni.pandora.horizon.model.event.Event;
import jakarta.validation.ConstraintViolation;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class InvalidEventBodyException extends HorizonStarlightException {

    public static final String DEFAULT_ERROR_MESSAGE = "Event did not pass the validation.";

    private String detail;

    public InvalidEventBodyException(Set<ConstraintViolation<Event>> violations) {
        super(DEFAULT_ERROR_MESSAGE);
        detail = violations.stream().sorted((a,b) -> StringUtils.compare(a.getPropertyPath().toString(), b.getPropertyPath().toString())).map(v -> String.format("Violation: %s", v.getMessage())).collect(Collectors.joining("; "));
    }

    public InvalidEventBodyException(String message) {
        super(message);
    }
}
