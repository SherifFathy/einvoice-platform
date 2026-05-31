package com.einvoice.eta.engine;

import com.einvoice.core.authority.AuthorityCredentials;
import com.einvoice.core.authority.AuthorityEngine;
import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.CancelInput;
import com.einvoice.core.authority.CertificateMaterial;
import com.einvoice.core.authority.DocumentInput;
import com.einvoice.core.authority.SerializedPayload;
import com.einvoice.core.authority.SignedPayload;
import com.einvoice.core.authority.StatusInput;
import com.einvoice.core.domain.config.EtaConfig;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.repository.config.EtaConfigRepository;
import com.einvoice.eta.client.EtaHttpClient;
import com.einvoice.eta.serialize.EtaInvoiceSerializer;
import com.einvoice.eta.serialize.EtaReceiptSerializer;
import com.einvoice.eta.sign.EtaSigningService;
import com.einvoice.eta.token.EtaTokenManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Engine that orchestrates ETA invoice/receipt serialization, signing, and submission. */
@Component
public class EtaAuthorityEngine implements AuthorityEngine {

    private final EtaInvoiceSerializer invoiceSerializer;
    private final EtaReceiptSerializer receiptSerializer;
    private final EtaSigningService signingService;
    private final EtaHttpClient httpClient;
    private final EtaTokenManager tokenManager;
    private final EtaConfigRepository configRepository;

    /** Holds the result of serialization and signing with credentials. */
    public record Preparation(SignedPayload signedPayload, String clientId,
            String clientSecret, String tokenUrl) {}

    /**
     * Constructs an EtaAuthorityEngine with the required dependencies.
     *
     * @param invoiceSerializer the invoice serializer
     * @param receiptSerializer the receipt serializer
     * @param signingService the signing service
     * @param httpClient the ETA HTTP client
     * @param tokenManager the ETA token manager
     * @param configRepository the ETA config repository
     */
    public EtaAuthorityEngine(EtaInvoiceSerializer invoiceSerializer,
            EtaReceiptSerializer receiptSerializer,
            EtaSigningService signingService,
            EtaHttpClient httpClient,
            EtaTokenManager tokenManager,
            EtaConfigRepository configRepository) {
        this.invoiceSerializer = invoiceSerializer;
        this.receiptSerializer = receiptSerializer;
        this.signingService = signingService;
        this.httpClient = httpClient;
        this.tokenManager = tokenManager;
        this.configRepository = configRepository;
    }

    /**
     * Serializes the given document input into a canonical payload.
     *
     * @param input the document input to serialize
     * @return the serialized payload
     */
    @Override
    public SerializedPayload serialize(DocumentInput input) {
        EtaInvoiceHeader header = (EtaInvoiceHeader) input.header();
        return invoiceSerializer.serialize(header);
    }

    /**
     * Signs the serialized payload with the provided certificate.
     *
     * @param payload the serialized payload to sign
     * @param cert the certificate material
     * @return the signed payload
     */
    @Override
    public SignedPayload sign(SerializedPayload payload,
            CertificateMaterial cert) {
        return signingService.sign(payload.canonicalBytes(), cert);
    }

    /**
     * Submits the signed payload to the ETA authority using credentials.
     *
     * @param signed the signed payload
     * @param creds the authority credentials
     * @return the authority response
     */
    @Override
    public AuthorityResponse submit(SignedPayload signed,
            AuthorityCredentials creds) {
        Short envId = httpClient.resolveEnvironmentId(
                creds.submissionUrl());
        if (envId == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve environment from URL: "
                            + creds.submissionUrl());
        }
        String token = tokenManager.getToken(creds.companyId(), envId,
                creds.clientId(), creds.clientSecret(),
                creds.tokenUrl());
        return httpClient.submitInvoice(token, envId,
                signed.canonicalBytes(), null);
    }

    /**
     * Prepares a submission by serializing and signing the invoice. Runs inside
     * a transaction so the config SELECT FOR UPDATE is covered.
     *
     * @param header the invoice header
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @return the preparation result containing signed payload and credentials
     */
    @Transactional
    public Preparation prepareSubmission(EtaInvoiceHeader header,
            UUID companyId, Short authorityEnvironmentId) {
        EtaConfig config = configRepository
                .findForUpdate(companyId, authorityEnvironmentId)
                .orElseThrow(() -> new com.einvoice.core.error
                        .NoCertificateConfiguredException(
                        "No ETA config for company",
                        companyId, authorityEnvironmentId));
        CertificateMaterial cert = new CertificateMaterial(
                config.getTokenName(), config.getTokenPass());
        SerializedPayload serialized = invoiceSerializer.serialize(header);
        SignedPayload signed = signingService.sign(
                serialized.canonicalBytes(), cert);
        return new Preparation(signed, config.getClientId(),
                config.getClientSecret1(), config.getTokenUrl());
    }

    /**
     * Submits a previously prepared payload to the ETA authority via HTTP.
     *
     * @param prep the preparation result from {@link #prepareSubmission}
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @param documentId the internal document identifier
     * @return the authority response
     */
    public AuthorityResponse httpSubmit(Preparation prep, UUID companyId,
            Short authorityEnvironmentId, UUID documentId) {
        String token = tokenManager.getToken(companyId,
                authorityEnvironmentId,
                prep.clientId(), prep.clientSecret(), prep.tokenUrl());
        return httpClient.submitInvoice(token, authorityEnvironmentId,
                prep.signedPayload().canonicalBytes(), documentId);
    }

    /**
     * Full pipeline: serialize, sign, and submit an invoice.
     *
     * @param header the invoice header
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @return the authority response
     */
    public AuthorityResponse submitInvoice(EtaInvoiceHeader header,
            UUID companyId, Short authorityEnvironmentId) {
        Preparation prep = prepareSubmission(header, companyId,
                authorityEnvironmentId);
        return httpSubmit(prep, companyId, authorityEnvironmentId,
                header.getId());
    }

    /**
     * Prepares a receipt submission by serializing and signing. Runs inside
     * a transaction so the config SELECT FOR UPDATE is covered.
     *
     * @param header the receipt header
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @return the preparation result containing signed payload and credentials
     */
    @Transactional
    public Preparation prepareReceiptSubmission(EtaReceiptHeader header,
            UUID companyId, Short authorityEnvironmentId) {
        EtaConfig config = configRepository
                .findForUpdate(companyId, authorityEnvironmentId)
                .orElseThrow(() -> new com.einvoice.core.error
                        .NoCertificateConfiguredException(
                        "No ETA config for company",
                        companyId, authorityEnvironmentId));
        CertificateMaterial cert = new CertificateMaterial(
                config.getTokenName(), config.getTokenPass());
        SerializedPayload serialized = receiptSerializer.serialize(header);
        SignedPayload signed = signingService.sign(
                serialized.canonicalBytes(), cert);
        return new Preparation(signed, config.getClientId(),
                config.getClientSecret1(), config.getTokenUrl());
    }

    /**
     * Submits a previously prepared receipt payload to ETA via HTTP.
     *
     * @param prep the preparation result
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @param documentId the internal document identifier
     * @return the authority response
     */
    public AuthorityResponse httpSubmitReceipt(Preparation prep,
            UUID companyId, Short authorityEnvironmentId, UUID documentId) {
        String token = tokenManager.getToken(companyId,
                authorityEnvironmentId,
                prep.clientId(), prep.clientSecret(), prep.tokenUrl());
        return httpClient.submitReceipt(token, authorityEnvironmentId,
                prep.signedPayload().canonicalBytes(), documentId);
    }

    /**
     * Full pipeline: serialize, sign, and submit a receipt.
     *
     * @param header the receipt header
     * @param companyId the company identifier
     * @param authorityEnvironmentId the ETA environment identifier
     * @return the authority response
     */
    public AuthorityResponse submitReceipt(EtaReceiptHeader header,
            UUID companyId, Short authorityEnvironmentId) {
        Preparation prep = prepareReceiptSubmission(header, companyId,
                authorityEnvironmentId);
        return httpSubmitReceipt(prep, companyId, authorityEnvironmentId,
                header.getId());
    }

    /**
     * Cancels a previously submitted document.
     *
     * @param input the cancel input containing company and document details
     * @return the authority response
     */
    @Override
    public AuthorityResponse cancel(CancelInput input) {
        EtaConfig config = configRepository
                .findForUpdate(input.companyId(),
                        input.authorityEnvironmentId())
                .orElseThrow(() -> new com.einvoice.core.error
                        .NoCertificateConfiguredException(
                        "No ETA config for company",
                        input.companyId(),
                        input.authorityEnvironmentId()));
        String token = tokenManager.getToken(input.companyId(),
                input.authorityEnvironmentId(),
                config.getClientId(), config.getClientSecret1(),
                config.getTokenUrl());
        return httpClient.cancelDocument(token,
                input.authorityEnvironmentId(),
                input.etaUuid(), input.reason());
    }

    /**
     * Checks the status of a previously submitted document.
     *
     * @param input the status input containing company and submission details
     * @return the authority response
     */
    @Override
    public AuthorityResponse checkStatus(StatusInput input) {
        EtaConfig config = configRepository
                .findForUpdate(input.companyId(),
                        input.authorityEnvironmentId())
                .orElseThrow(() -> new com.einvoice.core.error
                        .NoCertificateConfiguredException(
                        "No ETA config for company",
                        input.companyId(),
                        input.authorityEnvironmentId()));
        String token = tokenManager.getToken(input.companyId(),
                input.authorityEnvironmentId(),
                config.getClientId(), config.getClientSecret1(),
                config.getTokenUrl());
        return httpClient.getDocumentStatus(token,
                input.authorityEnvironmentId(),
                input.etaSubmissionId());
    }
}
