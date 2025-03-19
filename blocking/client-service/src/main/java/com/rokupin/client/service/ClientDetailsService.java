package com.rokupin.client.service;

import com.rokupin.client.model.user.Client;
import com.rokupin.client.model.user.ClientDetails;
import com.rokupin.client.repo.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDetailsService implements UserDetailsService {
    private final ClientRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Client> client = repo.findByUsername(username);
        if (client.isPresent()) {
            log.debug("Client {} found in db", username);
            return new ClientDetails(client.get());
        }
        log.debug("Client NOT {} found in db", username);
        throw new UsernameNotFoundException(username + " not found");
    }
}
