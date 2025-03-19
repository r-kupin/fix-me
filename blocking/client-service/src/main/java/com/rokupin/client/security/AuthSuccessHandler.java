package com.rokupin.client.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class AuthSuccessHandler implements AuthenticationSuccessHandler {
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request,
                                        final HttpServletResponse response,
                                        final Authentication authentication) throws IOException {
        final String targetUrl = "/home";

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        if (authentication instanceof UsernamePasswordAuthenticationToken tokenAuth) {
            String jwt = tokenAuth.getDetails().toString();
            log.debug("tokenAuth.getCredentials() -> {}", jwt);

            Cookie jwtCookie = new Cookie("JWT_TOKEN", jwt);
            jwtCookie.setHttpOnly(true); // Prevents access from JavaScript (XSS protection)
            jwtCookie.setSecure(false);  // TEMPORARY: Allow cookies over HTTP during development
            jwtCookie.setPath("/");      // Available to all endpoints
            jwtCookie.setMaxAge(60 * 60); // 1 hour expiry
            jwtCookie.setAttribute("SameSite", "Strict"); // Ensures it works for same-origin requests

            response.addCookie(jwtCookie);
        }
        redirectStrategy.sendRedirect(request, response, targetUrl);
    }
}
