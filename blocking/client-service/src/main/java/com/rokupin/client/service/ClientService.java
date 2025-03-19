package com.rokupin.client.service;

import com.rokupin.client.exceptions.ClientExistsException;
import com.rokupin.client.exceptions.PasswordsDoNotMatch;
import com.rokupin.client.exceptions.RegistrationException;
import com.rokupin.client.model.client.Client;
import com.rokupin.client.model.client.ClientRole;
import com.rokupin.client.model.form.SignupForm;
import com.rokupin.client.repo.ClientRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@AllArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registerUser(SignupForm form) throws RegistrationException {
        if (clientRepository.findByUsername(form.username()).isPresent())
            throw new ClientExistsException("Client '" + form.username() + "' already exists!");
        if (!form.password().equals(form.password_repeat()))
            throw new PasswordsDoNotMatch("Passwords do not match!");
        Client client = new Client(
                null,
                form.username(),
                passwordEncoder.encode(form.password()),
                Set.of(ClientRole.USER)
        );
        clientRepository.save(client);
    }
}
