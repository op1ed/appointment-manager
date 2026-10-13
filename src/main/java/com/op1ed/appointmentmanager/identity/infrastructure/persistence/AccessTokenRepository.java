package com.op1ed.appointmentmanager.identity.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.identity.domain.AccessToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {
    Optional<AccessToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from AccessToken t where t.tokenHash = :tokenHash")
    void revokeByTokenHash(@Param("tokenHash") String tokenHash);
}
