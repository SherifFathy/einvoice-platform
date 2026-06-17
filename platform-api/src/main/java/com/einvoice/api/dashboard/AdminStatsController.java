package com.einvoice.api.dashboard;

import com.einvoice.api.dashboard.dto.SystemStatsDto;
import com.einvoice.core.service.dashboard.AdminStatsQueryService;
import com.einvoice.core.service.dashboard.SystemStats;
import com.einvoice.security.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wave 9 Admin-Mode system-stats endpoint. Sits under {@code /api/admin/**} so
 * the existing security configuration requires the {@code SUPER_USER}
 * authority and {@link TenantFilter} admits {@code ADMIN_MODE} sessions to it
 * (reusing the same gating as the other {@code /api/admin/**} controllers).
 *
 * <p>An additional in-controller guard rejects any caller that is not a Super
 * User in {@code ADMIN_MODE}, returning {@code 403} via the global
 * {@code AccessDeniedException} handler. This keeps the contract test (which
 * runs with the filter chain disabled) able to assert the Admin-Mode / Super
 * User auth rule directly, and matches {@code contracts/dashboard.md}.
 *
 * <p>Returns counts only — never operational record content
 * (Constitution VII.3 / FR-007).
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsQueryService service;

    /**
     * Constructs the controller with its stats query service.
     *
     * @param service the Admin-Mode stats query service
     */
    public AdminStatsController(AdminStatsQueryService service) {
        this.service = service;
    }

    /**
     * Returns the counts-only system stats for the active authority
     * environment.
     *
     * @return the system-stats DTO
     */
    @GetMapping
    public SystemStatsDto stats() {
        requireAdminStatsAccess();
        SystemStats stats = service.stats(TenantContext.getAuthorityEnvironmentId());
        return new SystemStatsDto(
                stats.authorityEnvironmentId(),
                stats.totalCompanies(),
                stats.totalUsers(),
                stats.totalSubmissionsToday());
    }

    /**
     * Asserts the caller is a Super User in Admin Mode. Throws
     * {@link AccessDeniedException} (mapped to {@code 403} by the global
     * exception handler) otherwise.
     */
    private void requireAdminStatsAccess() {
        if (!TenantContext.isSuperUser()
                || TenantContext.getMode() != TenantContext.Mode.ADMIN_MODE) {
            throw new AccessDeniedException(
                    "Admin-Mode Super-User access required for system stats");
        }
    }
}
