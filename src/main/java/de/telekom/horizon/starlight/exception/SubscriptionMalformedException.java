// Copyright 2024-2025 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.exception;

public class SubscriptionMalformedException extends RuntimeException {
    public SubscriptionMalformedException(String message) {
        super(message);
    }

    public SubscriptionMalformedException(String message, Throwable cause) {
        super(message, cause);
    }
}
