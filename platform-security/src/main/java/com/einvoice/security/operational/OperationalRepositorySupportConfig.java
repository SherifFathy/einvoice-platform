package com.einvoice.security.operational;

import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.security.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/** Wires the OperationalRepositorySupport tenant-values supplier at application boot. */
@Configuration
public class OperationalRepositorySupportConfig {

    @PostConstruct
    void wireTenantValuesSupplier() {
        OperationalRepositorySupport.setTenantValuesSupplier(() -> {
            TenantContext.Holder ctx = TenantContext.current();
            if (ctx == null) {
                return null;
            }
            return new OperationalRepositorySupport.TenantValues(
                    ctx.companyId(), ctx.authorityEnvironmentId());
        });
    }
}
