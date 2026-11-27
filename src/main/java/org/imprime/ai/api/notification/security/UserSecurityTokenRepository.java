package org.imprime.ai.api.notification.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserSecurityTokenRepository extends JpaRepository<UserSecurityToken, Long> {

    Optional<UserSecurityToken> findByTokenHashAndPurpose(String tokenHash, UserSecurityTokenPurpose purpose);

    @Modifying
    @Query("""
            UPDATE UserSecurityToken t
               SET t.usedAt = :now
             WHERE t.userId = :userId
               AND t.purpose = :purpose
               AND t.usedAt IS NULL
            """)
    void invalidateActiveTokensByUserAndPurpose(Long userId, UserSecurityTokenPurpose purpose, Instant now);
}
