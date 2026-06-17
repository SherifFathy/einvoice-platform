package com.einvoice.core.repository.user;

import com.einvoice.core.domain.user.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByIsSuperUserTrueAndIsActiveTrue();

    long countByIsSuperUserTrueAndIsActiveTrueAndIdNot(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(UUID id);

    @Query("SELECT COUNT(u) FROM User u WHERE u.isSuperUser = true AND u.isActive = true")
    long countActiveSuperUsers();

    /**
     * Counts all active users platform-wide. Drives the Admin-Mode
     * {@code totalUsers} stat. Intentionally global (not env-scoped): the
     * {@code users} table has no environment dimension, and the dashboard
     * contract exposes a flat {@code totalUsers} with no env qualifier.
     *
     * @return the number of active users across the platform
     */
    long countByIsActiveTrue();
}
