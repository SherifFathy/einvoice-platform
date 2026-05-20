package com.einvoice.api.zatca.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaChainIntegrityIT extends AbstractZatcaChainIT {

    private static final int ITERATIONS = 100;

    private final AtomicInteger hashCounter = new AtomicInteger(0);

    @Override
    protected String testEmail() {
        return "chain-test@test.com";
    }

    @BeforeEach
    void setUp() {
        hashCounter.set(0);
        seedChainContext("Chain Integrity Co", "سلام",
                "CI001", "Chain Test Admin");

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenAnswer(inv -> {
                    int idx = hashCounter.incrementAndGet();
                    return new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                            ("<Invoice idx=\"" + idx + "\"/>").getBytes(),
                            ("<SignedInvoice idx=\"" + idx + "\"/>")
                                    .getBytes(),
                            "hash-chain-" + idx,
                            "base64qr-" + idx,
                            new byte[0]);
                });

        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    @Test
    void concurrentSubmissionsProduceConsecutiveCountersWithHashLinkage()
            throws Exception {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                Long.class, companyId));

        List<String> allDocIds = Collections.synchronizedList(
                new ArrayList<>());
        AtomicInteger docNumber = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (int iter = 0; iter < ITERATIONS; iter++) {
                String docIdA = createDraft(
                        "CI-A-" + docNumber.incrementAndGet());
                String docIdB = createDraft(
                        "CI-B-" + docNumber.incrementAndGet());

                CountDownLatch startGate = new CountDownLatch(1);
                CountDownLatch finishGate = new CountDownLatch(2);

                executor.submit(() -> {
                    try {
                        startGate.await();
                        submitDoc(docIdA);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        finishGate.countDown();
                    }
                });

                executor.submit(() -> {
                    try {
                        startGate.await();
                        submitDoc(docIdB);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        finishGate.countDown();
                    }
                });

                startGate.countDown();
                boolean completed = finishGate.await(
                        60, TimeUnit.SECONDS);
                assertEquals(true, completed,
                        "Iteration " + iter
                                + " did not complete within 60s");

                allDocIds.add(docIdA);
                allDocIds.add(docIdB);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        int expectedTotal = ITERATIONS * 2;
        assertEquals(expectedTotal, allDocIds.size(),
                "Expected " + expectedTotal + " documents");

        assertEquals(expectedTotal, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM submission_attempts "
                        + "WHERE company_id = ?",
                Integer.class, companyId),
                "Every submission should produce one attempt row");

        long finalCounter = jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                Long.class, companyId);
        assertEquals(expectedTotal, finalCounter,
                "Chain counter should be exactly " + expectedTotal
                        + " after all submissions");

        String finalChainHash = jdbcTemplate.queryForObject(
                "SELECT previous_invoice_hash FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                String.class, companyId);
        assertNotNull(finalChainHash);

        List<Map<String, Object>> headerRows = jdbcTemplate.queryForList(
                "SELECT id, invoice_counter_value, previous_invoice_hash, "
                        + "invoice_hash FROM zatca_standard_headers "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5 "
                        + "ORDER BY invoice_counter_value",
                companyId);

        assertEquals(expectedTotal, headerRows.size(),
                "No documents should be dropped or duplicated");

        List<Long> counterValues = new ArrayList<>();
        for (int i = 0; i < headerRows.size(); i++) {
            Map<String, Object> row = headerRows.get(i);
            Long cv = ((Number) row.get("invoice_counter_value"))
                    .longValue();
            String hash = (String) row.get("invoice_hash");
            assertNotNull(hash,
                    "invoice_hash must be populated for doc " + i);
            counterValues.add(cv);
        }

        Set<Long> uniqueCounters = new HashSet<>(counterValues);
        assertEquals(counterValues.size(), uniqueCounters.size(),
                "Counter values must be strictly unique");

        for (int i = 1; i < headerRows.size(); i++) {
            String prevHash = (String) headerRows.get(i)
                    .get("previous_invoice_hash");
            String earlierHash = (String) headerRows.get(i - 1)
                    .get("invoice_hash");
            if (prevHash != null && earlierHash != null) {
                assertEquals(earlierHash, prevHash,
                        "Document at index " + i
                                + " should reference "
                                + "the previous document's hash");
            }
        }

        Map<String, Object> lastDoc = headerRows.get(
                headerRows.size() - 1);
        String lastHash = (String) lastDoc.get("invoice_hash");
        assertEquals(lastHash, finalChainHash,
                "Chain state should reflect "
                        + "the last document's hash");
    }

    private void submitDoc(String docId) throws Exception {
        mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}/submit",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk());
    }
}
