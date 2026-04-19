package com.einvoice.eta;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.SubmissionResult;
import com.einvoice.core.service.AuthorityEngine;
import com.einvoice.core.service.SubmissionResultDto;
import com.einvoice.eta.client.EtaSubmissionClient;
import com.einvoice.eta.serializer.EtaInvoiceSerializer;
import com.einvoice.eta.signing.EtaSigningService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authority engine implementation for the Egyptian Tax Authority (ETA).
 */
@Service
public class EtaAuthorityEngine implements AuthorityEngine {

    private static final Logger log = LoggerFactory.getLogger(EtaAuthorityEngine.class);

    private final EtaInvoiceSerializer serializer;
    private final EtaSigningService signingService;
    private final EtaSubmissionClient submissionClient;

    /**
     * Creates a new EtaAuthorityEngine.
     *
     * @param serializer the ETA invoice serializer
     * @param signingService the ETA signing service
     * @param submissionClient the ETA submission client
     */
    public EtaAuthorityEngine(EtaInvoiceSerializer serializer,
            EtaSigningService signingService,
            EtaSubmissionClient submissionClient) {
        this.serializer = serializer;
        this.signingService = signingService;
        this.submissionClient = submissionClient;
    }

    @Override
    public Authority getSupportedAuthority() {
        return Authority.ETA;
    }

    @Override
    public String generatePayload(Invoice invoice, AuthorityConfig config) {
        String json = serializer.serialize(invoice);
        log.debug("Generated ETA JSON payload for invoice {}", invoice.getId());
        return json;
    }

    @Override
    public SubmissionResultDto submit(Invoice invoice, String payload,
            AuthorityConfig config) {
        try {
            EtaSigningService.EtaSigningResult signingResult =
                    signingService.sign(payload, config);

            Map<ArtifactType, String> artifacts = new HashMap<>();
            artifacts.put(ArtifactType.ETA_CADES_SIG, signingResult.signatureBase64());

            SubmissionResultDto result = submissionClient.submit(
                    payload, signingResult.signatureBase64(), config);

            if (result.status() == SubmissionResult.SUCCESS
                    && result.externalReference() != null) {
                invoice.setExternalInvoiceReference(result.externalReference());
            }

            return new SubmissionResultDto(
                    result.status(),
                    result.httpStatusCode(),
                    null,
                    result.authorityResponse(),
                    result.warnings(),
                    result.errors(),
                    artifacts,
                    result.externalReference());
        } catch (Exception e) {
            log.error("ETA submission failed for invoice {}", invoice.getId(), e);
            return SubmissionResultDto.error("ETA submission failed: " + e.getMessage());
        }
    }

    @Override
    public ArtifactType getPayloadArtifactType() {
        return ArtifactType.SIGNED_JSON;
    }

    @Override
    public ArtifactType getResponseArtifactType() {
        return ArtifactType.ETA_RESPONSE;
    }

    @Override
    public ArtifactType getClearedArtifactType() {
        return null;
    }

    @Override
    public List<ArtifactType> getExpectedArtifactTypes() {
        return List.of(ArtifactType.SIGNED_JSON, ArtifactType.ETA_CADES_SIG,
                ArtifactType.ETA_RESPONSE);
    }
}
