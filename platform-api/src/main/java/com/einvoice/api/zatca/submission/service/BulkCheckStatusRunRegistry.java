package com.einvoice.api.zatca.submission.service;

import com.einvoice.security.tenant.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory registry of bulk check-status run IDs and their cancel flags.
 *
 * <p>Each run records the {@code userId} and {@code companyId} from
 * the creating request so that cancellation can be restricted to the
 * owning user (or a super-user in operational mode).</p>
 */
@Component
public class BulkCheckStatusRunRegistry {

    private static final Logger log = LoggerFactory.getLogger(
            BulkCheckStatusRunRegistry.class);

    private final ConcurrentHashMap<String, RunEntry> runs =
            new ConcurrentHashMap<>();

    /**
     * Create a new run entry and register it.
     *
     * <p>The entry is stamped with the current
     * {@link TenantContext#getUserId()} and
     * {@link TenantContext#getCompanyId()} for ownership verification
     * on cancel.</p>
     *
     * @return the newly created run entry
     */
    public RunEntry createRun() {
        String runId = UUID.randomUUID().toString();
        UUID ownerUserId = TenantContext.getUserId();
        UUID ownerCompanyId = TenantContext.getCompanyId();
        RunEntry entry = new RunEntry(runId, ownerUserId, ownerCompanyId);
        runs.put(runId, entry);
        return entry;
    }

    /**
     * Set the cancel flag for a run after verifying ownership.
     *
     * <p>Only the user who created the run, or a super-user in
     * operational mode, may cancel it. Returns {@code false} if
     * the run is not found or the caller is not authorised.</p>
     *
     * @param runId the run ID to cancel
     * @return true if the run was found and the caller is authorised
     */
    public boolean cancel(String runId) {
        RunEntry entry = runs.get(runId);
        if (entry == null) {
            return false;
        }
        if (!isOwnerOrSuper(entry)) {
            return false;
        }
        entry.cancelled.set(true);
        return true;
    }

    /**
     * Check whether a run has been cancelled.
     *
     * @param runId the run ID to check
     * @return true if the run is cancelled
     */
    public boolean isCancelled(String runId) {
        RunEntry entry = runs.get(runId);
        return entry != null && entry.cancelled.get();
    }

    /**
     * Mark a run as completed.
     *
     * @param runId the run ID to mark completed
     */
    public void markCompleted(String runId) {
        RunEntry entry = runs.get(runId);
        if (entry != null) {
            entry.completed.set(true);
            entry.completedAt = Instant.now();
        }
    }

    /** Garbage-collect completed runs older than 10 minutes. */
    @Scheduled(fixedDelay = 300_000)
    public void gcCompleted() {
        Instant cutoff = Instant.now().minusSeconds(600);
        for (Map.Entry<String, RunEntry> e : runs.entrySet()) {
            if (e.getValue().completed.get()
                    && e.getValue().completedAt != null
                    && e.getValue().completedAt.isBefore(cutoff)) {
                runs.remove(e.getKey());
                log.debug("GC'd completed bulk run {}", e.getKey());
            }
        }
    }

    private boolean isOwnerOrSuper(RunEntry entry) {
        UUID callerUserId = TenantContext.getUserId();
        if (callerUserId == null) {
            return false;
        }
        if (TenantContext.isSuperUser()
                && TenantContext.getMode()
                        == TenantContext.Mode.OPERATIONAL_MODE) {
            return true;
        }
        return callerUserId.equals(entry.ownerUserId);
    }

    /** Tracks a single bulk check-status run. */
    public static class RunEntry {
        public final String runId;
        public final UUID ownerUserId;
        public final UUID ownerCompanyId;
        public final AtomicBoolean cancelled = new AtomicBoolean(false);
        public final AtomicBoolean completed = new AtomicBoolean(false);
        public volatile Instant completedAt;

        /**
         * Construct with the given run ID and owner.
         *
         * @param runId the unique run identifier
         * @param ownerUserId the user who initiated the run
         * @param ownerCompanyId the company context of the run
         */
        public RunEntry(String runId, UUID ownerUserId,
                UUID ownerCompanyId) {
            this.runId = runId;
            this.ownerUserId = ownerUserId;
            this.ownerCompanyId = ownerCompanyId;
        }
    }
}
