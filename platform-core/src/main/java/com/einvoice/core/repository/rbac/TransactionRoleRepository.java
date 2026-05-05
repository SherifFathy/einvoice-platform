package com.einvoice.core.repository.rbac;

import com.einvoice.core.domain.rbac.TransactionRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface TransactionRoleRepository extends JpaRepository<TransactionRole, UUID> {

    Optional<TransactionRole> findByAuthorityAndTransactionTypeAndRoleCode(
            String authority, String transactionType, String roleCode);
}
