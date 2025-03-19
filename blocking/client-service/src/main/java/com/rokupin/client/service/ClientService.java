package com.rokupin.client.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ClientService {
//    private final ClientRepository clientRepository;
//    private final PasswordEncoder passwordEncoder;
//    private final AuthenticationManager authManager;
//    private final JwtService jwtService;
//
//    @Transactional
//    public void registerUser(SignupForm form) {
//        if (clientRepository.findByUsername(form.username()).isPresent())
//            throw new RuntimeException("Client '" + form.username() + "' already exists!");
//        if (!form.password().equals(form.password_repeat()))
//            throw new RuntimeException("Passwords do not match!");
//        Client client = new Client(
//                null,
//                form.username(),
//                passwordEncoder.encode(form.password()),
//                Set.of(ClientRole.USER)
//        );
//        clientRepository.save(client);
//    }
//
//    public String verify(Client client) {
//        Authentication auth = authManager.authenticate(
//                new UsernamePasswordAuthenticationToken(
//                        client.getUsername(), client.getPassword())
//        );
//        return auth.isAuthenticated() ? jwtService.generateToken(client) : "KO";
//    }
}
