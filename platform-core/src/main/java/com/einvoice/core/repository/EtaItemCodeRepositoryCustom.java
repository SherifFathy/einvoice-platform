package com.einvoice.core.repository;

import com.einvoice.core.domain.EtaItemCode;
import com.einvoice.core.domain.enums.EtaItemCodeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Custom repository methods for filtered ETA item code queries.
 */
public interface EtaItemCodeRepositoryCustom {

    Page<EtaItemCode> findByCompanyIdFiltered(Long companyId,
            EtaItemCodeStatus status, String search, Pageable pageable);
}
