package com.einvoice.api.eta.artifact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.InvoiceArtifact;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.repository.shared.InvoiceArtifactRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.security.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EtaArtifactControllerContractTest {

    @Mock
    private InvoiceArtifactRepository artifactRepository;

    @Mock
    private SubmissionAttemptRepository attemptRepository;

    private MockMvc mockMvc;

    private UUID companyId;
    private UUID docId;

    @BeforeEach
    void setUp() {
        EtaArtifactController controller = new EtaArtifactController(
                artifactRepository, attemptRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        companyId = UUID.randomUUID();
        docId = UUID.randomUUID();

        TenantContext.set(new TenantContext.Holder(
                UUID.randomUUID(), companyId, (short) 2,
                "ETA", "TEST", TenantContext.Mode.OPERATIONAL_MODE,
                false, System.currentTimeMillis(), "jti"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void downloadArtifact_returns200WithCorrectHeaders() throws Exception {
        String bodyContent = "{\"signed\":true}";
        String hash = sha256Hex(bodyContent);

        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .transactionType(TransactionType.INVOICE)
                .documentId(docId)
                .artifactType(ArtifactType.SIGNED_JSON)
                .attemptNumber(1)
                .content(bodyContent)
                .contentHash(hash)
                .createdAt(OffsetDateTime.now())
                .build();

        when(artifactRepository.findByDocumentIdAndTypeAndTenant(
                docId, ArtifactType.SIGNED_JSON, companyId, (short) 2,
                TransactionType.INVOICE))
                .thenReturn(List.of(artifact));

        mockMvc.perform(get(
                "/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_JSON",
                companyId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Artifact-Hash", hash))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString(
                                MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(bodyContent));
    }

    @Test
    void downloadArtifact_returns404WhenNotFound() throws Exception {
        when(artifactRepository.findByDocumentIdAndTypeAndTenant(
                any(), any(ArtifactType.class), any(UUID.class),
                any(Short.class), any(TransactionType.class)))
                .thenReturn(List.of());

        mockMvc.perform(get(
                "/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_JSON",
                companyId, docId))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadArtifact_invalidType_returns400() throws Exception {
        mockMvc.perform(get(
                "/api/companies/{companyId}/eta/invoices/{docId}/artifacts/INVALID_TYPE",
                companyId, docId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadArtifact_withAttemptNumber_filtersByAttempt() throws Exception {
        String bodyContent = "{\"signed\":true}";
        String hash = sha256Hex(bodyContent);

        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .transactionType(TransactionType.INVOICE)
                .documentId(docId)
                .artifactType(ArtifactType.SIGNED_JSON)
                .attemptNumber(2)
                .content(bodyContent)
                .contentHash(hash)
                .createdAt(OffsetDateTime.now())
                .build();

        when(artifactRepository.findByDocumentIdAndTypeAndAttemptAndTenant(
                docId, ArtifactType.SIGNED_JSON, 2, companyId, (short) 2,
                TransactionType.INVOICE))
                .thenReturn(List.of(artifact));

        mockMvc.perform(get(
                "/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_JSON",
                companyId, docId)
                .param("attemptNumber", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Artifact-Hash", hash));
    }

    @Test
    void downloadArtifact_xmlType_returnsXmlContentType() throws Exception {
        String xmlContent = "<document>signed</document>";
        String hash = sha256Hex(xmlContent);

        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .transactionType(TransactionType.INVOICE)
                .documentId(docId)
                .artifactType(ArtifactType.SIGNED_XML)
                .attemptNumber(1)
                .content(xmlContent)
                .contentHash(hash)
                .createdAt(OffsetDateTime.now())
                .build();

        when(artifactRepository.findByDocumentIdAndTypeAndTenant(
                docId, ArtifactType.SIGNED_XML, companyId, (short) 2,
                TransactionType.INVOICE))
                .thenReturn(List.of(artifact));

        mockMvc.perform(get(
                "/api/companies/{companyId}/eta/invoices/{docId}/artifacts/SIGNED_XML",
                companyId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString(
                                MediaType.APPLICATION_XML_VALUE)))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".xml")));
    }

    @Test
    void listSubmissions_returnsAttemptHistory() throws Exception {
        SubmissionAttempt attempt = SubmissionAttempt.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .authorityEnvironmentId((short) 2)
                .transactionType(TransactionType.INVOICE)
                .documentId(docId)
                .attemptNumber(1)
                .submittedBy(UUID.randomUUID())
                .result(SubmissionResult.SUCCESS)
                .submittedAt(OffsetDateTime.now())
                .build();

        when(attemptRepository.findByDocumentIdAndTenant(
                docId, companyId, TransactionType.INVOICE))
                .thenReturn(List.of(attempt));

        mockMvc.perform(get(
                "/api/companies/{companyId}/eta/invoices/{docId}/submissions",
                companyId, docId))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("SUCCESS")));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
