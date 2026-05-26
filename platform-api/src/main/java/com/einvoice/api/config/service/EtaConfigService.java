package com.einvoice.api.config.service;

import com.einvoice.api.config.dto.EtaConfigResponse;
import com.einvoice.api.config.dto.EtaConfigWriteRequest;
import com.einvoice.core.domain.config.EtaConfig;
import com.einvoice.core.error.BranchIdNotAllowedException;
import com.einvoice.core.error.ProductionTokenRequiredException;
import com.einvoice.core.repository.config.EtaConfigRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.security.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing ETA configuration. */
@Service
@Transactional
public class EtaConfigService {

    private static final short ETA_PRODUCTION_ENV_ID = 1;

    private final EtaConfigRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public EtaConfigService(EtaConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Reads the current ETA configuration for the active tenant.
     *
     * @return the ETA configuration response, or an empty shell if none exists
     */
    @Transactional(readOnly = true)
    public EtaConfigResponse read() {
        return repository.findOne(OperationalRepositorySupport.<EtaConfig>inActiveTenant())
                .map(this::toResponse)
                .orElseGet(this::emptyShell);
    }

    /**
     * Replaces the ETA configuration with the provided values.
     *
     * @param request the configuration write payload
     * @return the updated ETA configuration response
     */
    public EtaConfigResponse replace(EtaConfigWriteRequest request) {
        if (request.branchId() != null) {
            throw new BranchIdNotAllowedException(
                    "branchId is not allowed for config endpoints");
        }
        validateProductionTokenFields(request);

        UUID companyId = TenantContext.getCompanyId();
        Short envId = TenantContext.getAuthorityEnvironmentId();

        return repository.findForUpdate(companyId, envId)
                .map(existing -> toResponse(updateEntity(existing, request)))
                .orElseGet(() -> insertOrRetry(request, companyId, envId));
    }

    private EtaConfigResponse insertOrRetry(EtaConfigWriteRequest request, UUID companyId, Short envId) {
        EtaConfig entity = buildNewEntity(request, companyId, envId);
        try {
            repository.saveAndFlush(entity);
            return toResponse(entity);
        } catch (DataIntegrityViolationException e) {
            entityManager.clear();
            return repository.findForUpdate(companyId, envId)
                    .map(existing -> toResponse(updateEntity(existing, request)))
                    .orElseThrow(() -> e);
        }
    }

    private void validateProductionTokenFields(EtaConfigWriteRequest request) {
        Short envId = TenantContext.getAuthorityEnvironmentId();
        if (envId != null && envId == ETA_PRODUCTION_ENV_ID) {
            if (request.tokenName() == null || request.tokenName().isBlank()) {
                throw new ProductionTokenRequiredException(
                        "tokenName", request.tokenName());
            }
            if (request.tokenPass() == null || request.tokenPass().isBlank()) {
                throw new ProductionTokenRequiredException(
                        "tokenPass", request.tokenPass());
            }
        }
    }

    private EtaConfig buildNewEntity(EtaConfigWriteRequest request, UUID companyId, Short envId) {
        return EtaConfig.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .clientId(request.clientId())
                .clientSecret1(request.clientSecret1())
                .clientSecret2(request.clientSecret2())
                .tokenName(request.tokenName())
                .tokenPass(request.tokenPass())
                .submissionUrl(request.submissionUrl())
                .tokenUrl(request.tokenUrl())
                .posSerial(request.posSerial())
                .posOsVersion(request.posOsVersion())
                .posModel(request.posModel())
                .build();
    }

    private EtaConfig updateEntity(EtaConfig entity, EtaConfigWriteRequest request) {
        entity.setClientId(request.clientId());
        entity.setClientSecret1(request.clientSecret1());
        entity.setClientSecret2(request.clientSecret2());
        entity.setTokenName(request.tokenName());
        entity.setTokenPass(request.tokenPass());
        entity.setSubmissionUrl(request.submissionUrl());
        entity.setTokenUrl(request.tokenUrl());
        entity.setPosSerial(request.posSerial());
        entity.setPosOsVersion(request.posOsVersion());
        entity.setPosModel(request.posModel());
        return repository.save(entity);
    }

    private EtaConfigResponse toResponse(EtaConfig c) {
        return new EtaConfigResponse(
                c.getId(), c.getCompanyId(), null,
                c.getClientId(), c.getClientSecret1(), c.getClientSecret2(),
                c.getTokenName(), c.getTokenPass(),
                c.getSubmissionUrl(), c.getTokenUrl(),
                c.getPosSerial(), c.getPosOsVersion(), c.getPosModel(),
                c.getIsActive(), toUtc(c.getCreatedAt()), toUtc(c.getUpdatedAt()));
    }

    private EtaConfigResponse emptyShell() {
        UUID companyId = TenantContext.getCompanyId();
        return new EtaConfigResponse(
                null, companyId, null,
                null, null, null,
                null, null,
                null, null,
                null, null, null,
                true, null, null);
    }

    private static OffsetDateTime toUtc(OffsetDateTime odt) {
        return odt != null ? odt.withOffsetSameInstant(ZoneOffset.UTC) : null;
    }
}
