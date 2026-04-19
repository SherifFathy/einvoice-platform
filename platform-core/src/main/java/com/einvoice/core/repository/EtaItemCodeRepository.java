package com.einvoice.core.repository;

import com.einvoice.core.domain.EtaItemCode;
import com.einvoice.core.domain.enums.EtaItemCodeStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Tenant-scoped repository for {@link EtaItemCode} data access. */
@Repository
public interface EtaItemCodeRepository extends JpaRepository<EtaItemCode, Long>,
        EtaItemCodeRepositoryCustom {

    List<EtaItemCode> findByCompanyId(Long companyId);

    List<EtaItemCode> findByCompanyIdAndStatus(Long companyId,
            EtaItemCodeStatus status);

    boolean existsByCompanyIdAndItemCode(Long companyId, String itemCode);
}
