package com.rokupin.client.security.jwt;

import com.rokupin.client.service.ClientDetailsService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class JwtAuthProvider implements AuthenticationProvider {
    private final ClientDetailsService clientDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        UserDetails userDetails = clientDetailsService.loadUserByUsername(username);

        log.debug("Authentication for: {} {}", userDetails.getUsername(), userDetails.getPassword());

        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            log.debug("Authentication failed: passwords don't match");
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtUtils.generateToken(userDetails);

        log.debug("Token generated: {}", token);

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        authenticationToken.setDetails(token);
        log.debug("Authentication token details: {}", authenticationToken.getDetails());
        return authenticationToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
