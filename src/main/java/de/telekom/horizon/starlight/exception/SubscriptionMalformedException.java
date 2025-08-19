package de.telekom.horizon.starlight.exception;

public class SubscriptionMalformedException extends RuntimeException {
    public SubscriptionMalformedException(String message) {
        super(message);
    }

    public SubscriptionMalformedException(String message, Throwable cause) {
        super(message, cause);
    }
}
