package com.rokupin.client.service;

import com.rokupin.client.model.user.Client;
import com.rokupin.client.model.user.ClientDetails;
import com.rokupin.client.repo.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientDetailsService implements UserDetailsService {
    private final ClientRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Client> client = repo.findByUsername(username);
        if (client.isPresent())
            return new ClientDetails(client.get());
        throw new UsernameNotFoundException(username + " not found");
    }
}
