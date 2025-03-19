package com.rokupin.client.exceptions;

public class ClientExistsException extends RegistrationException {
    public ClientExistsException(String message) {
        super(message);
    }
}
