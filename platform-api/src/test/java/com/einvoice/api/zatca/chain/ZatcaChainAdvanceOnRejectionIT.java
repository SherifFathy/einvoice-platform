package com.einvoice.api.zatca.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaChainAdvanceOnRejectionIT extends AbstractZatcaChainIT {

    @Override
    protected String testEmail() {
        return "rejection-chain@test.com";
    }

    @BeforeEach
    void setUp() {
        seedChainContext("Rejection Chain Co", "مرفوض",
                "RC001", "Rejection Test Admin");
    }

    @Test
    void rejectionAdvancesChainAndNextSubmissionReferencesRejectedHash()
            throws Exception {
        String rejectedDocId = createDraft("REJ-001");

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hash-rejected-doc",
                        "base64qr-rejected",
                        new byte[0]));

        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        false, "NOT_CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        400, "Invalid buyer VAT number",
                        "{\"error\":\"NOT_CLEARED\"}"));

        mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}/submit",
                                companyId, rejectedDocId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.clearanceStatus")
                        .value("NOT_CLEARED"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                Long.class, companyId),
                "Chain counter should advance by 1 even on rejection");

        String rejectedInvoiceHash = jdbcTemplate.queryForObject(
                "SELECT invoice_hash FROM zatca_standard_headers "
                        + "WHERE id = ?",
                String.class, UUID.fromString(rejectedDocId));
        assertNotNull(rejectedInvoiceHash,
                "Rejected document must have an invoice_hash");

        String chainHashAfterRejection = jdbcTemplate.queryForObject(
                "SELECT previous_invoice_hash FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                String.class, companyId);
        assertEquals(rejectedInvoiceHash, chainHashAfterRejection,
                "Chain state should reference "
                        + "the rejected document's hash");

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine.ZatcaSubmissionResult(
                        "<Invoice/>".getBytes(),
                        "<SignedInvoice/>".getBytes(),
                        "hash-successful-doc",
                        "base64qr-success",
                        new byte[0]));

        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, "{}"));

        String successDocId = createDraft("SUC-001");

        mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}/submit",
                                companyId, successDocId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACCEPTED"));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND "
                        + "authority_environment_id = 5",
                Long.class, companyId),
                "Chain counter should be 2 after rejection + success");

        String successPrevHash = jdbcTemplate.queryForObject(
                "SELECT previous_invoice_hash FROM "
                        + "zatca_standard_headers WHERE id = ?",
                String.class, UUID.fromString(successDocId));
        assertEquals(rejectedInvoiceHash, successPrevHash,
                "Successful document's previous_invoice_hash "
                        + "must equal the rejected document's "
                        + "invoice_hash");
    }
}
