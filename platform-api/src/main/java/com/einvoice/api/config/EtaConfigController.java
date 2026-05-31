package com.einvoice.api.config;

import com.einvoice.api.config.dto.EtaConfigResponse;
import com.einvoice.api.config.dto.EtaConfigWriteRequest;
import com.einvoice.api.config.service.EtaConfigService;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.operational.RequireOperationalMode;
import com.einvoice.security.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for ETA configuration. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/eta/config")
public class EtaConfigController {

    private final EtaConfigService service;

    public EtaConfigController(EtaConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(transactionType = "CONFIG", action = "VIEW")
    public ResponseEntity<EtaConfigResponse> read(@PathVariable UUID companyId) {
        verifyContext(companyId);
        return ResponseEntity.ok(service.read());
    }

    @PutMapping
    @RequiresPermission(transactionType = "CONFIG", action = "EDIT")
    public ResponseEntity<EtaConfigResponse> replace(
            @PathVariable UUID companyId,
            @Valid @RequestBody EtaConfigWriteRequest request) {
        verifyContext(companyId);
        return ResponseEntity.ok(service.replace(request));
    }

    private void verifyContext(UUID pathCompanyId) {
        UUID jwtCompanyId = TenantContext.getCompanyId();
        if (jwtCompanyId == null || !jwtCompanyId.equals(pathCompanyId)) {
            throw new UnauthorizedContextException(
                    "Path companyId does not match authenticated company context");
        }
    }
}
