package com.einvoice.core.repository;

import com.einvoice.core.domain.UserEnvironmentPermission;
import com.einvoice.core.domain.enums.Environment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for querying user environment permission assignments. */
@Repository
public interface UserEnvironmentPermissionRepository
        extends JpaRepository<UserEnvironmentPermission, Long> {

    List<UserEnvironmentPermission> findByUserCompanyRoleId(Long userCompanyRoleId);

    List<UserEnvironmentPermission> findByUserCompanyRoleIdAndEnvironmentIn(
            Long userCompanyRoleId, List<Environment> environments);

    boolean existsByUserCompanyRoleIdAndEnvironment(
            Long userCompanyRoleId, Environment environment);

    List<UserEnvironmentPermission> findByUserCompanyRoleUserIdAndUserCompanyRoleCompanyId(
            Long userId, Long companyId);
}
