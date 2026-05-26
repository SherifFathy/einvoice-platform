package com.einvoice.api.config;

import com.einvoice.api.config.dto.ZatcaConfigResponse;
import com.einvoice.api.config.dto.ZatcaConfigWriteRequest;
import com.einvoice.api.config.service.ZatcaConfigService;
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

/** REST controller for ZATCA configuration. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca/config")
public class ZatcaConfigController {

    private final ZatcaConfigService service;

    public ZatcaConfigController(ZatcaConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission(transactionType = "CONFIG", action = "VIEW")
    public ResponseEntity<ZatcaConfigResponse> read(@PathVariable UUID companyId) {
        verifyContext(companyId);
        return ResponseEntity.ok(service.read());
    }

    @PutMapping
    @RequiresPermission(transactionType = "CONFIG", action = "EDIT")
    public ResponseEntity<ZatcaConfigResponse> replace(
            @PathVariable UUID companyId,
            @Valid @RequestBody ZatcaConfigWriteRequest request) {
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
