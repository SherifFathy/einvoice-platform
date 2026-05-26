package com.einvoice.api.config.service;

import com.einvoice.api.config.dto.ZatcaConfigResponse;
import com.einvoice.api.config.dto.ZatcaConfigWriteRequest;
import com.einvoice.core.domain.config.ZatcaConfig;
import com.einvoice.core.domain.zatca.ZatcaChainState;
import com.einvoice.core.error.BranchIdNotAllowedException;
import com.einvoice.core.error.InvalidExpiryDateException;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.repository.zatca.ZatcaChainStateRepository;
import com.einvoice.security.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing ZATCA configuration. */
@Service
@Transactional
public class ZatcaConfigService {

    private final ZatcaConfigRepository repository;
    private final ZatcaChainStateRepository chainStateRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ZatcaConfigService(ZatcaConfigRepository repository,
                              ZatcaChainStateRepository chainStateRepository) {
        this.repository = repository;
        this.chainStateRepository = chainStateRepository;
    }

    /**
     * Reads the current ZATCA configuration for the active tenant.
     *
     * @return the ZATCA configuration response, or an empty shell if none exists
     */
    @Transactional(readOnly = true)
    public ZatcaConfigResponse read() {
        return repository.findOne(OperationalRepositorySupport.<ZatcaConfig>inActiveTenant())
                .map(this::toResponse)
                .orElseGet(this::emptyShell);
    }

    /**
     * Replaces the ZATCA configuration with the provided values.
     *
     * @param request the configuration write payload
     * @return the updated ZATCA configuration response
     */
    public ZatcaConfigResponse replace(ZatcaConfigWriteRequest request) {
        if (request.branchId() != null) {
            throw new BranchIdNotAllowedException(
                    "branchId is not allowed for config endpoints");
        }

        UUID companyId = TenantContext.getCompanyId();
        Short envId = TenantContext.getAuthorityEnvironmentId();

        ZatcaConfig saved = repository.findForUpdate(companyId, envId)
                .map(existing -> updateEntity(existing, request))
                .orElseGet(() -> insertOrRetry(request, companyId, envId));

        ensureChainStateExists(companyId, envId);

        return toResponseWithChainState(saved, true);
    }

    /**
     * Wave 6 accepted trade-off: recovering from {@link DataIntegrityViolationException}
     * inside the same transaction via {@code entityManager.clear()} relies on the
     * constraint-violation flush not having issued a partial SQL batch. This mirrors
     * {@code EtaConfigService} and is acceptable for Wave 6 single-row upserts.
     * A future hardening pass may isolate this in a {@code REQUIRES_NEW} inner TX.
     */
    private ZatcaConfig insertOrRetry(ZatcaConfigWriteRequest request,
                                       UUID companyId, Short envId) {
        ZatcaConfig entity = buildNewEntity(request, companyId, envId);
        try {
            repository.saveAndFlush(entity);
            return entity;
        } catch (DataIntegrityViolationException e) {
            entityManager.clear();
            return repository.findForUpdate(companyId, envId)
                    .map(existing -> updateEntity(existing, request))
                    .orElseThrow(() -> e);
        }
    }

    private void ensureChainStateExists(UUID companyId, Short envId) {
        boolean exists = chainStateRepository
                .findByCompanyAndAuthorityEnvironment(companyId, envId)
                .isPresent();
        if (!exists) {
            ZatcaChainState chainState = ZatcaChainState.builder()
                    .companyId(companyId)
                    .authorityEnvironmentId(envId)
                    .build();
            try {
                chainStateRepository.saveAndFlush(chainState);
            } catch (DataIntegrityViolationException ignored) {
                entityManager.clear();
            }
        }
    }

    private ZatcaConfig buildNewEntity(ZatcaConfigWriteRequest request,
                                        UUID companyId, Short envId) {
        return ZatcaConfig.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .privateKey(request.privateKey())
                .deviceUuid(request.deviceUuid())
                .csr(request.csr())
                .complianceCertificate(request.complianceCertificate())
                .complianceApiSecret(request.complianceApiSecret())
                .productionCertificate(request.productionCertificate())
                .productionApiSecret(request.productionApiSecret())
                .certificateExpiryDate(parseExpiryDate(request.certificateExpiryDate()))
                .build();
    }

    private ZatcaConfig updateEntity(ZatcaConfig entity, ZatcaConfigWriteRequest request) {
        entity.setPrivateKey(request.privateKey());
        entity.setDeviceUuid(request.deviceUuid());
        entity.setCsr(request.csr());
        entity.setComplianceCertificate(request.complianceCertificate());
        entity.setComplianceApiSecret(request.complianceApiSecret());
        entity.setProductionCertificate(request.productionCertificate());
        entity.setProductionApiSecret(request.productionApiSecret());
        entity.setCertificateExpiryDate(parseExpiryDate(request.certificateExpiryDate()));
        return repository.save(entity);
    }

    private LocalDate parseExpiryDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeException e) {
            throw new InvalidExpiryDateException(
                    "Invalid certificateExpiryDate: " + dateStr,
                    "certificateExpiryDate", dateStr);
        }
    }

    private ZatcaConfigResponse toResponse(ZatcaConfig c) {
        boolean chainInitialized = chainStateInitialized(c.getCompanyId(), c.getAuthorityEnvironmentId());
        return toResponseWithChainState(c, chainInitialized);
    }

    private ZatcaConfigResponse toResponseWithChainState(ZatcaConfig c, boolean chainStateInitialized) {
        return new ZatcaConfigResponse(
                c.getId(), c.getCompanyId(),
                c.getPrivateKey(), c.getDeviceUuid(), c.getCsr(),
                c.getComplianceCertificate(), c.getComplianceApiSecret(),
                c.getProductionCertificate(), c.getProductionApiSecret(),
                c.getCertificateExpiryDate(),
                chainStateInitialized,
                c.getIsActive(), toUtc(c.getCreatedAt()), toUtc(c.getUpdatedAt()));
    }

    private boolean chainStateInitialized(UUID companyId, Short envId) {
        return chainStateRepository
                .findByCompanyAndAuthorityEnvironment(companyId, envId)
                .isPresent();
    }

    private ZatcaConfigResponse emptyShell() {
        UUID companyId = TenantContext.getCompanyId();
        return new ZatcaConfigResponse(
                null, companyId,
                null, null, null,
                null, null,
                null, null,
                null,
                false,
                null, null, null);
    }

    private static OffsetDateTime toUtc(OffsetDateTime odt) {
        return odt != null ? odt.withOffsetSameInstant(ZoneOffset.UTC) : null;
    }
}
