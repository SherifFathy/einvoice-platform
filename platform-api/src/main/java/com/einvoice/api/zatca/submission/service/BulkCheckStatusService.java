package com.einvoice.api.zatca.submission.service;

import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedService;
import com.einvoice.api.zatca.standard.service.ZatcaStandardService;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executes bulk ZATCA document status checks with rate pacing and NDJSON streaming.
 *
 * <p>TenantContext is captured before entering the streaming lambda and
 * restored on the worker thread for each document, mirroring the pattern
 * in {@code BulkStatusCheckExecutor} from Wave 7.</p>
 */
@Service
public class BulkCheckStatusService {

    private static final Logger log = LoggerFactory.getLogger(
            BulkCheckStatusService.class);

    private static final long CALL_INTERVAL_MS = 300;

    private final BulkCheckStatusRunRegistry registry;
    private final ZatcaSubmissionOrchestrator orchestrator;
    private final ZatcaStandardService standardService;
    private final ZatcaSimplifiedService simplifiedService;
    private final ObjectMapper objectMapper;

    /**
     * Inject dependencies.
     *
     * @param registry the run registry
     * @param orchestrator the submission orchestrator
     * @param standardService the standard document service
     * @param simplifiedService the simplified document service
     * @param objectMapper the JSON object mapper
     */
    public BulkCheckStatusService(BulkCheckStatusRunRegistry registry,
            ZatcaSubmissionOrchestrator orchestrator,
            ZatcaStandardService standardService,
            ZatcaSimplifiedService simplifiedService,
            ObjectMapper objectMapper) {
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.standardService = standardService;
        this.simplifiedService = simplifiedService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new run and execute bulk check-status.
     *
     * @param capturedContext the TenantContext captured on the request thread
     * @param documentIds the document IDs to check
     * @param txType the transaction type (STANDARD or SIMPLIFIED)
     * @param out the output stream for NDJSON lines
     * @return the run ID
     */
    public String runBulk(TenantContext.Holder capturedContext,
            List<UUID> documentIds, TransactionType txType,
            OutputStream out) {
        return runWithExistingRun(registry.createRun(), capturedContext,
                documentIds, txType, out);
    }

    /**
     * Execute bulk check-status using an existing run entry.
     *
     * <p>The caller <b>must</b> capture the {@link TenantContext.Holder}
     * on the request thread (before entering the streaming lambda) and
     * pass it here. This method restores the context for each document
     * processed, ensuring tenant isolation is preserved on the async
     * worker thread. Reading the ThreadLocal inside this method would
     * return {@code null} because Spring's
     * {@code StreamingResponseBody} executes after the request thread
     * has cleared the context.</p>
     *
     * @param run the pre-created run entry
     * @param capturedContext the TenantContext captured on the request thread
     * @param documentIds the document IDs to check
     * @param txType the transaction type
     * @param out the output stream for NDJSON lines
     * @return the run ID
     */
    public String runWithExistingRun(BulkCheckStatusRunRegistry.RunEntry run,
            TenantContext.Holder capturedContext,
            List<UUID> documentIds, TransactionType txType,
            OutputStream out) {
        String runId = run.runId;

        try {
            for (int i = 0; i < documentIds.size(); i++) {
                if (run.cancelled.get()) {
                    for (int j = i; j < documentIds.size(); j++) {
                        emitOutcome(out, documentIds.get(j),
                                "CANCELLED_NO_OP", null, null, null);
                    }
                    break;
                }

                UUID docId = documentIds.get(i);
                try {
                    if (capturedContext != null) {
                        TenantContext.set(capturedContext);
                    }
                    processSingle(docId, txType, out);
                } finally {
                    TenantContext.clear();
                }

                if (i < documentIds.size() - 1) {
                    Thread.sleep(CALL_INTERVAL_MS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Bulk run {} interrupted", runId);
        } finally {
            registry.markCompleted(runId);
        }

        return runId;
    }

    private void processSingle(UUID docId, TransactionType txType,
            OutputStream out) {
        try {
            if (txType == TransactionType.STANDARD) {
                processStandard(docId, out);
            } else {
                processSimplified(docId, out);
            }
        } catch (Exception e) {
            log.warn("Bulk check failed for doc {}: {}", docId,
                    e.toString(), e);
            emitOutcome(out, docId, "ZATCA_ERROR", null, null,
                    e.getMessage());
        }
    }

    private void processStandard(UUID docId, OutputStream out) {
        ZatcaStandardHeader header;
        try {
            header = standardService.loadWithinTenant(docId);
        } catch (Exception e) {
            emitOutcome(out, docId, "NOT_FOUND", null, null, null);
            return;
        }

        DocumentState before = header.getStatus();
        var outcome = orchestrator.checkStandardStatus(header);
        DocumentState after = outcome.header().getStatus();

        String result;
        if (after != before) {
            result = "UPDATED";
        } else {
            result = "UNCHANGED";
        }
        emitOutcome(out, docId, result, before.name(), after.name(), null);
    }

    private void processSimplified(UUID docId, OutputStream out) {
        ZatcaSimplifiedHeader header;
        try {
            header = simplifiedService.loadWithinTenant(docId);
        } catch (Exception e) {
            emitOutcome(out, docId, "NOT_FOUND", null, null, null);
            return;
        }

        DocumentState before = header.getStatus();
        var outcome = orchestrator.checkSimplifiedStatus(header);
        DocumentState after = outcome.header().getStatus();

        String result;
        if (after != before) {
            result = "UPDATED";
        } else {
            result = "UNCHANGED";
        }
        emitOutcome(out, docId, result, before.name(), after.name(), null);
    }

    private void emitOutcome(OutputStream out, UUID docId, String outcome,
            String beforeState, String afterState, String errorSummary) {
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("documentId", docId.toString());
            line.put("outcome", outcome);
            line.put("beforeState", beforeState);
            line.put("afterState", afterState);
            line.put("errorSummary", errorSummary);
            byte[] bytes = objectMapper.writeValueAsBytes(line);
            out.write(bytes);
            out.write('\n');
            out.flush();
        } catch (Exception e) {
            log.error("Failed to emit NDJSON outcome for {}", docId, e);
        }
    }
}
