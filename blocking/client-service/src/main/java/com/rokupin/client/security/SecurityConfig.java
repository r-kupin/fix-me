package com.rokupin.client.security;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    //    private final UserDetailsService clientDetailsService;
    private final Filter jwtFilter;
    private final AuthenticationProvider authProvider;
    private final AuthenticationSuccessHandler authSuccessHandler;

    public SecurityConfig(
            @Qualifier("jwtFilter") Filter jwtFilter,
            AuthenticationProvider authProvider,
            AuthenticationSuccessHandler authSuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.authProvider = authProvider;
        this.authSuccessHandler = authSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(registry ->
                        registry.requestMatchers("/login", "/signup")
                                .permitAll()
                                .anyRequest()
                                .authenticated()
                )
                .authenticationProvider(authProvider)
                .formLogin(login ->
                                login.loginPage("/login")
                                        .defaultSuccessUrl("/home")
                                        .successHandler(authSuccessHandler)
                                        .failureUrl("/login?login=failed")
                                        .permitAll()
                )
                .addFilterAfter(
                        jwtFilter, UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

//    @Bean
//    public AuthenticationManager authenticationManager(
//            UserDetailsService clientDetailsService,
//            PasswordEncoder passwordEncoder) {
//        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
//        authProvider.setUserDetailsService(clientDetailsService);
//        authProvider.setPasswordEncoder(passwordEncoder);
//        return new ProviderManager(authProvider);
//    }
}
