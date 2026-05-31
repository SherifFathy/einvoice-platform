package com.einvoice.core.repository.rbac;

import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface AuthorityEnvironmentRepository extends JpaRepository<AuthorityEnvironment, Short> {

    Optional<AuthorityEnvironment> findByAuthorityAndEnvironmentAndIsActiveTrue(String authority, String environment);

    List<AuthorityEnvironment> findByIsActiveTrueOrderById();
}
