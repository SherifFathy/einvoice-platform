package com.einvoice.api.zatca.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ZatcaChainBusyTimeoutIT extends AbstractZatcaChainIT {

    @Autowired private DataSource dataSource;

    @Override
    protected String testEmail() {
        return "chain-busy@test.com";
    }

    @BeforeEach
    void setUp() {
        seedChainContext("Chain Busy Co", "مشغول",
                "CB001", "Chain Busy Admin");

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hash-busy-test",
                        "base64qr-busy",
                        new byte[0]));

        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));
    }

    @Test
    void submitReturns503WhenChainRowIsLocked() throws Exception {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                Long.class, companyId));

        final String docId = createDraft("BUSY-001");

        Connection lockConn = dataSource.getConnection();
        Statement lockStmt = null;
        try {
            lockConn.setAutoCommit(false);
            lockStmt = lockConn.createStatement();
            lockStmt.executeQuery(
                    "SELECT * FROM zatca_chain_state "
                            + "WHERE company_id = '" + companyId
                            + "' AND authority_environment_id = 5 "
                            + "FOR UPDATE");

            final AtomicInteger submitStatus = new AtomicInteger();
            long startNanos = System.nanoTime();

            CompletableFuture<Void> submitFuture = CompletableFuture
                    .runAsync(() -> {
                        try {
                            var result = mockMvc.perform(
                                            post("/api/companies/{companyId}"
                                                            + "/zatca/standard"
                                                            + "/{docId}/submit",
                                                    companyId, docId)
                                                    .header("Authorization",
                                                            "Bearer " + token))
                                    .andReturn();
                            submitStatus.set(result.getResponse()
                                    .getStatus());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

            submitFuture.get(45, TimeUnit.SECONDS);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startNanos);

            assertEquals(503, submitStatus.get(),
                    "Submit should return HTTP 503 SERVICE_UNAVAILABLE");

            assertTrue(elapsedMs < 35_000,
                    "Submit should resolve within ~30s bounded "
                            + "wait. Took " + elapsedMs + "ms");

            String docJson = mockMvc.perform(
                            get("/api/companies/{companyId}"
                                            + "/zatca/standard/{docId}",
                                    companyId, docId)
                                    .header("Authorization",
                                            "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertTrue(docJson.contains("\"status\":\"DRAFT\""),
                    "Document should remain in DRAFT state");

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT invoice_counter FROM zatca_chain_state "
                            + "WHERE company_id = ? AND "
                            + "authority_environment_id = 5",
                    Long.class, companyId),
                    "Chain counter should be unchanged");

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM submission_attempts "
                            + "WHERE document_id = ?",
                    Integer.class, UUID.fromString(docId)),
                    "No submission_attempts row should be inserted");
        } finally {
            if (lockStmt != null) {
                try {
                    lockStmt.close();
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
            if (lockConn != null) {
                try {
                    lockConn.rollback();
                    lockConn.close();
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
        }
    }
}
