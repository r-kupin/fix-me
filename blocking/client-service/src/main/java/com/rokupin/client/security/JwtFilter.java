package com.rokupin.client.security;

import com.rokupin.client.service.ClientDetailsService;
import com.rokupin.client.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final ClientDetailsService clientDetailsService;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null, username = null;

        log.debug("JwtFilter headers: {}", Collections.list(request.getHeaderNames()));
        log.debug("JwtFilter path: {}", request.getRequestURL());


        if (Objects.nonNull(header) && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    log.debug("cookie: {} : {}", cookie.getName(), cookie.getValue());
                    if ("JWT_TOKEN".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (Objects.nonNull(token)) {
            try {
                username = jwtService.extractUsername(token);
            } catch (Exception e) {
                log.warn("Invalid JWT Token: {}", e.getMessage());
            }
        }

        if (Objects.nonNull(username) &&
                Objects.isNull(SecurityContextHolder.getContext().getAuthentication())) {
            UserDetails userDetails =
                    clientDetailsService.loadUserByUsername(username);
            if (jwtService.validate(token, userDetails)) {
                log.debug("User {} with token {} is validated", username, token);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}