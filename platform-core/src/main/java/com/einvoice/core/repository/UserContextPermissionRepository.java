package com.einvoice.core.repository;

import com.einvoice.core.domain.UserContextPermission;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for querying user-context-scoped permission grants. */
@Repository
public interface UserContextPermissionRepository
        extends JpaRepository<UserContextPermission, Long> {

    @Query("SELECT ucp FROM UserContextPermission ucp "
            + "WHERE ucp.user.id = :userId "
            + "AND ucp.company.id = :companyId "
            + "AND ucp.lovContext.id = :lovContextId")
    List<UserContextPermission> findByUserIdAndCompanyIdAndLovContextId(
            @Param("userId") Long userId,
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId);

    @Query("SELECT ucp.permission FROM UserContextPermission ucp "
            + "WHERE ucp.user.id = :userId "
            + "AND ucp.company.id = :companyId "
            + "AND ucp.lovContext.id = :lovContextId")
    Set<String> findPermissionsByUserIdAndCompanyIdAndLovContextId(
            @Param("userId") Long userId,
            @Param("companyId") Long companyId,
            @Param("lovContextId") Long lovContextId);
}
