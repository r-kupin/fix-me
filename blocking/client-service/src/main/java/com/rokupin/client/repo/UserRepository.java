package com.rokupin.client.repo;

import com.rokupin.client.model.UserEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<UserEntry, Long> {
    Optional<UserEntry> findByLogin(String Login);
//    Set<UserEntry> findByRoleContaining(Role role);
}
