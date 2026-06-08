package com.einvoice.core.service.dashboard;

import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.util.UtcDateRange;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the counts-only Admin-Mode system-stats read-model for the active
 * authority environment (US2 / Constitution VII.3). Returns scalars only —
 * never operational record content.
 *
 * <p>Scope rules (pinned in {@code contracts/dashboard.md}):
 * <ul>
 *   <li>{@code totalCompanies} — env-scoped: distinct active companies
 *       registered (via an active transaction role) in the environment
 *       (mirrors the US1 card-set definition).</li>
 *   <li>{@code totalUsers} — global active users across the platform (the
 *       {@code users} table has no environment dimension and the contract
 *       exposes a flat {@code totalUsers}).</li>
 *   <li>{@code totalSubmissionsToday} — env-scoped submission attempts
 *       submitted in the half-open {@code [startOfUtcDay, startOfNextUtcDay)}
 *       window (UTC, never server-local; research R3).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsQueryService {

    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final UserRepository userRepository;
    private final SubmissionAttemptRepository submissionAttemptRepository;
    private final Clock clock;

    /**
     * Constructs the Admin-Mode stats query service.
     *
     * @param uctrRepository user-company-role repository (env-scoped companies)
     * @param userRepository user repository (global active-user count)
     * @param submissionAttemptRepository submission attempt repository
     *        (env-scoped submissions-today count)
     * @param clock UTC clock for the "today" day boundary
     */
    public AdminStatsQueryService(
            UserCompanyTransactionRoleRepository uctrRepository,
            UserRepository userRepository,
            SubmissionAttemptRepository submissionAttemptRepository,
            Clock clock) {
        this.uctrRepository = uctrRepository;
        this.userRepository = userRepository;
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.clock = clock;
    }

    /**
     * Computes the Admin-Mode system stats for an environment.
     *
     * @param authorityEnvironmentId the active authority environment; when
     *        {@code null} all counts are returned as zero
     * @return the counts-only system-stats read-model
     */
    public SystemStats stats(Short authorityEnvironmentId) {
        if (authorityEnvironmentId == null) {
            return new SystemStats(null, 0L, 0L, 0L);
        }
        UtcDateRange.Range today = UtcDateRange.todayRange(clock);
        long totalCompanies = uctrRepository
                .countDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(
                        authorityEnvironmentId);
        long totalUsers = userRepository.countByIsActiveTrue();
        long totalSubmissionsToday = submissionAttemptRepository
                .countByAuthorityEnvironmentIdAndSubmittedAtBetween(
                        authorityEnvironmentId, today.from(), today.to());
        return new SystemStats(
                authorityEnvironmentId,
                totalCompanies,
                totalUsers,
                totalSubmissionsToday);
    }
}
