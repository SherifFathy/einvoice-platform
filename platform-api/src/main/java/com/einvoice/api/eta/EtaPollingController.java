package com.einvoice.api.eta;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.eta.polling.EtaStatusPollingService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoints for controlling ETA background polling. */
@RestController
@RequestMapping("/api/admin/eta-polling")
public class EtaPollingController {

    private final AuthorityConfigRepository authorityConfigRepository;
    private final EtaStatusPollingService pollingService;

    @Value("${polling.eta.interval-ms:300000}")
    private long pollIntervalMs;

    public EtaPollingController(AuthorityConfigRepository authorityConfigRepository,
            EtaStatusPollingService pollingService) {
        this.authorityConfigRepository = authorityConfigRepository;
        this.pollingService = pollingService;
    }

    /**
     * Returns the current polling status for the tenant.
     *
     * @return polling status details
     */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Long companyId = TenantContext.getCurrentTenantId();

        var configs = authorityConfigRepository.findByCompanyIdAndAuthority(
                companyId, Authority.ETA);

        boolean enabled = configs.stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getPollingEnabled()));

        int inReviewCount = pollingService.countInReviewInvoices(companyId);

        int pollIntervalMinutes = (int) (pollIntervalMs / 60000);

        return ResponseEntity.ok(Map.of(
                "companyId", companyId,
                "pollingEnabled", enabled,
                "lastPollAt", pollingService.getLastPollAt()
                        .map(java.time.OffsetDateTime::toString).orElse("never"),
                "invoicesInReview", inReviewCount,
                "pollIntervalMinutes", pollIntervalMinutes
        ));
    }

    /**
     * Stops background polling for the tenant.
     *
     * @return confirmation response
     */
    @PostMapping("/stop")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> stop() {
        Long companyId = TenantContext.getCurrentTenantId();
        updatePollingEnabled(companyId, false);
        return ResponseEntity.ok(Map.of(
                "companyId", companyId,
                "pollingEnabled", false,
                "message", "Background polling stopped"
        ));
    }

    /**
     * Resumes background polling for the tenant.
     *
     * @return confirmation response
     */
    @PostMapping("/resume")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> resume() {
        Long companyId = TenantContext.getCurrentTenantId();
        updatePollingEnabled(companyId, true);
        return ResponseEntity.ok(Map.of(
                "companyId", companyId,
                "pollingEnabled", true,
                "message", "Background polling resumed"
        ));
    }

    private void updatePollingEnabled(Long companyId, boolean enabled) {
        authorityConfigRepository.findByCompanyIdAndAuthority(companyId, Authority.ETA)
                .forEach(c -> {
                    c.setPollingEnabled(enabled);
                    authorityConfigRepository.save(c);
                });
    }
}
