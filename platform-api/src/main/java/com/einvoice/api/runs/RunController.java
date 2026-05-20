package com.einvoice.api.runs;

import com.einvoice.api.zatca.submission.service.BulkCheckStatusRunRegistry;
import com.einvoice.security.operational.RequireOperationalMode;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for cancelling bulk check-status runs.
 *
 * <p>Access is gated solely by ownership verification inside
 * {@link BulkCheckStatusRunRegistry#cancel(String)}: only the user
 * who created the run (or a super-user in operational mode) may
 * cancel it. No {@code @RequiresPermission} annotation is used
 * because a single run may cover STANDARD or SIMPLIFIED documents,
 * and requiring a specific module permission would block users who
 * only hold the other module's REFRESH action.</p>
 */
@RestController
@RequireOperationalMode
@RequestMapping("/api/runs")
public class RunController {

    private final BulkCheckStatusRunRegistry registry;

    /**
     * Inject the run registry.
     *
     * @param registry the bulk check-status run registry
     */
    public RunController(BulkCheckStatusRunRegistry registry) {
        this.registry = registry;
    }

    /**
     * Cancel a running bulk check-status operation.
     *
     * <p>Returns 200 if the run was found and the caller is the owner
     * (or a super-user). Returns 404 if the run ID is unknown or the
     * caller is not authorised to cancel it.</p>
     *
     * @param runId the run ID to cancel
     * @return 200 with cancellation confirmation, or 404
     */
    @DeleteMapping("/{runId}")
    public ResponseEntity<Map<String, Object>> cancelRun(
            @PathVariable String runId) {
        boolean found = registry.cancel(runId);
        if (!found) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("runId", runId,
                "cancelled", true));
    }
}
