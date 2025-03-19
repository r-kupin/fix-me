package com.rokupin.client.exceptions;

public class PasswordsDoNotMatch extends RegistrationException {
    public PasswordsDoNotMatch(String message) {
        super(message);
    }
}
