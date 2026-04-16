package com.einvoice.core.repository;

import com.einvoice.core.domain.UserCompanyRole;
import com.einvoice.core.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for UserCompanyRole entity operations. */
@Repository
public interface UserCompanyRoleRepository extends JpaRepository<UserCompanyRole, Long> {

    List<UserCompanyRole> findByUserId(Long userId);

    List<UserCompanyRole> findByCompanyId(Long companyId);

    Optional<UserCompanyRole> findByUserIdAndCompanyId(Long userId, Long companyId);

    List<UserCompanyRole> findByUserIdAndIsActiveTrue(Long userId);

    boolean existsByUserIdAndCompanyId(Long userId, Long companyId);

    List<UserCompanyRole> findByCompanyIdAndRole(Long companyId, Role role);
}
