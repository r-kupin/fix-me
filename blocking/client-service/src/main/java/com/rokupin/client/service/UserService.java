package com.rokupin.client.service;

import com.rokupin.client.model.Role;
import com.rokupin.client.model.UserEntry;
import com.rokupin.client.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void registerUser(String username, String rawPassword) {
        if (userRepository.findByLogin(username).isPresent()) {
            throw new RuntimeException("Username with this login already exists!");
        }
        UserEntry user = new UserEntry(
                null,
                username,
                passwordEncoder.encode(rawPassword),
                Set.of(Role.USER)
        );
        userRepository.save(user);
    }
}
