package com.rokupin.client.model;

import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "user")
public record UserEntry(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id,
        @Column(unique = true, nullable = false)
        String login,
        @Column(nullable = false)
        String password,
        @Column(nullable = false)
        Set<Role> role
) {
}
