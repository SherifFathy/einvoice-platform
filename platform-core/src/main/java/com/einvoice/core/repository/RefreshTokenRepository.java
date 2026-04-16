package com.einvoice.core.repository;

import com.einvoice.core.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for RefreshToken entity operations. */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Finds a refresh token by its token string.
     *
     * @param token the token string
     * @return the matching refresh token, if any
     */
    Optional<RefreshToken> findByToken(String token);
}
