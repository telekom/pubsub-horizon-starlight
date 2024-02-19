package de.telekom.horizon.starlight.exception;

public class PayloadTooLargeException extends HorizonStarlightException {
    public PayloadTooLargeException(String message, Throwable e) { super(message, e); }

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
