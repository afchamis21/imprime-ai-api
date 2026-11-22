package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findAuthTokenByTokenHash(String hash);

    @Modifying
    @Query(value = """
    UPDATE AUTH_TOKEN
       SET REVOKED = true
     WHERE USER_ID = :userId
       AND (REVOKED = false OR REVOKED IS NULL)
    """, nativeQuery = true)
    void revokeTokensByUserId(Long userId);

    @Modifying
    @Query(value = """
    UPDATE AUTH_TOKEN
       SET REVOKED = true
     WHERE AUTH_TOKEN_ID = :authTokenId
    """, nativeQuery = true)
    void revokeToken(Long authTokenId);
}
