package com.einvoice.api.integration.service;

import com.einvoice.core.domain.ingestion.entity.InboundPayloadArchive;
import com.einvoice.core.domain.ingestion.repository.InboundPayloadArchiveRepository;
import com.einvoice.core.error.InboundPayloadArchiveException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for archiving inbound payloads in a separate transaction (FR-OBS-003).
 * The archive write always succeeds — even malformed JSON is stored as a
 * wrapped envelope so FR-OBS-003 ("every inbound request") is satisfied.
 */
@Service
public class InboundPayloadArchiveService {

    private final InboundPayloadArchiveRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new InboundPayloadArchiveService.
     *
     * @param repository the archive repository
     * @param objectMapper the object mapper
     */
    public InboundPayloadArchiveService(InboundPayloadArchiveRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Archives the raw request body in a REQUIRES_NEW transaction.
     * Valid JSON is stored as-is; malformed JSON is wrapped so the
     * archive write always succeeds (FR-OBS-003).
     *
     * @param endpoint the request URI
     * @param body the raw request body bytes
     * @return the generated archive row UUID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID archive(String endpoint, byte[] body) {
        try {
            String bodyToStore = sanitiseBody(body);
            InboundPayloadArchive entity = InboundPayloadArchive.builder()
                    .endpoint(endpoint)
                    .body(bodyToStore)
                    .build();
            InboundPayloadArchive saved = repository.save(entity);
            return saved.getId();
        } catch (Exception e) {
            throw new InboundPayloadArchiveException(
                    "Inbound payload archive unavailable; ingestion aborted.", e);
        }
    }

    /**
     * Patches the outcome, tenancy tuple, and (on success) the document pointer
     * on the archive row. Uses a @Modifying JPQL query so the entity stays
     * immutable beyond these post-hoc patches.
     *
     * @param id the archive row UUID
     * @param httpStatus the HTTP status code
     * @param companyId the resolved company ID (may be null)
     * @param authorityEnvironmentId the resolved authority environment ID (may be null)
     * @param documentId the persisted document UUID (null when the request was rejected)
     * @param documentType ETA_INVOICE / ETA_RECEIPT / ZATCA_STANDARD / ZATCA_SIMPLIFIED (null when rejected)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void patchOutcome(UUID id, int httpStatus, UUID companyId, Short authorityEnvironmentId,
            UUID documentId, String documentType) {
        repository.patchOutcome(id, (short) httpStatus, companyId, authorityEnvironmentId,
                documentId, documentType);
    }

    private String sanitiseBody(byte[] body) {
        String raw = new String(body, StandardCharsets.UTF_8);
        try {
            objectMapper.readTree(body);
            return raw;
        } catch (Exception parseError) {
            Map<String, String> envelope = new LinkedHashMap<>();
            envelope.put("_raw", Base64.getEncoder().encodeToString(body));
            envelope.put("_parseError", parseError.getMessage());
            try {
                return objectMapper.writeValueAsString(envelope);
            } catch (Exception jsonError) {
                return "{\"_parseError\":\"unarchivable body\"}";
            }
        }
    }
}
