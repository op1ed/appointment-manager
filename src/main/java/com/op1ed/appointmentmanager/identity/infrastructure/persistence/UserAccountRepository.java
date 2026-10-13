package com.op1ed.appointmentmanager.identity.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.identity.domain.UserAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}
