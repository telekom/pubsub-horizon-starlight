package de.telekom.horizon.starlight.exception;

import de.telekom.eni.pandora.horizon.common.exception.HorizonException;

public abstract class HorizonStarlightException extends HorizonException {

    protected HorizonStarlightException(String message, Throwable e) {
        super(message, e);
    }

    protected HorizonStarlightException(String message) {
        super(message);
    }
}
