package de.telekom.horizon.starlight.exception;

public class RealmDoesNotMatchEnvironmentException extends HorizonStarlightException {
	public RealmDoesNotMatchEnvironmentException(String realm, String environment) {
		super(String.format("Realm %s does not match environment %s.", realm, environment));
	}
}
