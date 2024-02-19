// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.exception;

public class CouldNotPublishEventMessageException extends HorizonStarlightException {

    public CouldNotPublishEventMessageException(String message, Throwable t) {
        super(message, t);
    }
}
