// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.exception;

public class PayloadTooLargeException extends HorizonStarlightException {
    public PayloadTooLargeException(String message, Throwable e) { super(message, e); }

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
