package com.einvoice.core.repository.rbac;

import com.einvoice.core.domain.rbac.TransactionRolePermission;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface TransactionRolePermissionRepository extends JpaRepository<TransactionRolePermission, UUID> {

    @Query("SELECT trp.permissionCode FROM TransactionRolePermission trp WHERE trp.transactionRole.id = :roleId")
    Set<String> findPermissionCodesByRoleId(UUID roleId);

    @Query("SELECT trp FROM TransactionRolePermission trp WHERE trp.transactionRole.id = :roleId")
    List<TransactionRolePermission> findByRoleId(UUID roleId);
}
