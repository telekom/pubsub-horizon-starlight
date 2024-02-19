// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.exception;

public class EventNotCompliantWithSchemaException extends HorizonStarlightException {

    public EventNotCompliantWithSchemaException(String message, Throwable t) {
        super(message, t);
    }
}
