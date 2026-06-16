package com.einvoice.core.repository.shared;

import com.einvoice.core.domain.shared.PlatformBranding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for the single platform branding row. */
@Repository
public interface PlatformBrandingRepository
        extends JpaRepository<PlatformBranding, Short> {
}
