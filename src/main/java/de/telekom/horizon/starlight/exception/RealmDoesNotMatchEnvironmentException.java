// Copyright 2024 Deutsche Telekom IT GmbH
//
// SPDX-License-Identifier: Apache-2.0

package de.telekom.horizon.starlight.exception;

public class RealmDoesNotMatchEnvironmentException extends HorizonStarlightException {
	public RealmDoesNotMatchEnvironmentException(String realm, String environment) {
		super(String.format("Realm %s does not match environment %s.", realm, environment));
	}
}
