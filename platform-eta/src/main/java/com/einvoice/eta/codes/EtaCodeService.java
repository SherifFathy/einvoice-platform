package com.einvoice.eta.codes;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.EtaItemCode;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.EtaItemCodeStatus;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.EtaItemCodeRepository;
import com.einvoice.eta.auth.EtaTokenManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Service for managing ETA item code registrations.
 * Handles local persistence and ETA API communication.
 * <p>
 * Resolves the active ETA authority config by preferring ETA_PREPRODUCTION
 * (sandbox) for code registration; falls back to the first active ETA config
 * of any environment.
 */
@Service
public class EtaCodeService {

    private static final Logger log = LoggerFactory.getLogger(EtaCodeService.class);

    private final EtaItemCodeRepository etaItemCodeRepository;
    private final AuthorityConfigRepository authorityConfigRepository;
    private final EtaTokenManager tokenManager;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    /**
     * Creates a new EtaCodeService.
     *
     * @param etaItemCodeRepository the ETA item code repository
     * @param authorityConfigRepository the authority config repository
     * @param tokenManager the ETA token manager
     * @param objectMapper the JSON object mapper
     * @param restClientBuilder the REST client builder
     * @param entityManager the JPA entity manager for reference lookups
     */
    public EtaCodeService(EtaItemCodeRepository etaItemCodeRepository,
            AuthorityConfigRepository authorityConfigRepository,
            EtaTokenManager tokenManager,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder,
            EntityManager entityManager) {
        this.etaItemCodeRepository = etaItemCodeRepository;
        this.authorityConfigRepository = authorityConfigRepository;
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(30));
        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    /**
     * Registers a new item code with ETA.
     *
     * @param companyId the tenant company ID
     * @param itemCode the item code value
     * @param codeType the code classification type
     * @param description optional description
     * @return the persisted ETA item code
     * @throws DuplicateEtaItemCodeException if the code already exists for this company
     */
    @Transactional
    @Audited(action = "eta.code.register", entityType = "EtaItemCode")
    public EtaItemCode registerCode(Long companyId, String itemCode,
            String codeType, String description) {
        if (etaItemCodeRepository.existsByCompanyIdAndItemCode(companyId, itemCode)) {
            throw new DuplicateEtaItemCodeException(
                    "Duplicate item code for this company: " + itemCode);
        }

        EtaItemCode code = EtaItemCode.builder()
                .company(entityManager.getReference(Company.class, companyId))
                .itemCode(itemCode)
                .codeType(codeType)
                .description(description)
                .status(EtaItemCodeStatus.PENDING)
                .build();

        EtaItemCode saved = etaItemCodeRepository.save(code);

        Optional<AuthorityConfig> etaConfigOpt = resolveEtaConfig(companyId);
        if (etaConfigOpt.isPresent()) {
            try {
                String etaCodeId = submitToEta(saved, etaConfigOpt.get());
                if (etaCodeId != null) {
                    saved.setEtaCodeId(etaCodeId);
                    saved.setStatus(EtaItemCodeStatus.APPROVED);
                    saved = etaItemCodeRepository.save(saved);
                }
            } catch (Exception e) {
                log.warn("ETA code registration API call failed, keeping PENDING: {}",
                        e.getMessage());
            }
        }

        return saved;
    }

    /**
     * Lists item codes for the current tenant with optional filters.
     *
     * @param companyId the tenant company ID
     * @param status optional status filter
     * @param search optional search term for item code
     * @param pageable pagination parameters
     * @return a page of ETA item codes
     */
    public Page<EtaItemCode> listCodes(Long companyId, EtaItemCodeStatus status,
            String search, Pageable pageable) {
        return etaItemCodeRepository.findByCompanyIdFiltered(
                companyId, status, search, pageable);
    }

    /**
     * Searches ETA's published code directory.
     *
     * @param companyId the tenant company ID
     * @param query the search query
     * @param pageable pagination parameters
     * @return a page of published code results from ETA
     * @throws EtaSearchException if the ETA API call fails
     */
    public Page<JsonNode> searchPublishedCodes(Long companyId, String query,
            Pageable pageable) {
        Optional<AuthorityConfig> configOpt = resolveEtaConfig(companyId);
        if (configOpt.isEmpty()) {
            throw new EtaSearchException(
                    "No active ETA configuration found for this company");
        }

        AuthorityConfig config = configOpt.get();
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/codes")
                .queryParam("pageNo", pageable.getPageNumber())
                .queryParam("pageSize", pageable.getPageSize())
                .queryParam("query", query)
                .build()
                .toUri();

        try {
            String response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode items = root.path("items");
                if (items.isArray()) {
                    long total = root.path("totalElements").asLong(items.size());
                    return new PageImpl<>(toList(items), pageable, total);
                }
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                tokenManager.invalidateToken(
                        config.getBranch().getId(), config.getEnvironment());
            }
            throw new EtaSearchException(
                    "ETA published codes search failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new EtaSearchException(
                    "ETA published codes search error: " + e.getMessage(), e);
        }

        return Page.empty(pageable);
    }

    /**
     * Updates an existing item code registration.
     *
     * @param companyId the tenant company ID
     * @param codeId the item code database ID
     * @param itemCode the updated item code value
     * @param codeType the updated code type
     * @param description the updated description
     * @return the updated ETA item code
     * @throws EtaItemCodeNotFoundException if the code does not exist
     * @throws EtaItemCodeAccessDeniedException if the code belongs to another tenant
     */
    @Transactional
    @Audited(action = "eta.code.update", entityType = "EtaItemCode")
    public EtaItemCode updateCode(Long companyId, Long codeId,
            String itemCode, String codeType, String description) {
        EtaItemCode existing = etaItemCodeRepository.findById(codeId)
                .orElseThrow(() -> new EtaItemCodeNotFoundException(
                        "Item code not found: " + codeId));

        if (!existing.getCompany().getId().equals(companyId)) {
            throw new EtaItemCodeAccessDeniedException(
                    "Item code not found: " + codeId);
        }

        existing.setItemCode(itemCode);
        existing.setCodeType(codeType);
        existing.setDescription(description);

        resolveEtaConfig(companyId).ifPresent(config -> {
            if (existing.getEtaCodeId() != null) {
                try {
                    updateOnEta(existing, config);
                } catch (Exception e) {
                    log.warn("ETA code update API call failed: {}", e.getMessage());
                }
            }
        });

        return etaItemCodeRepository.save(existing);
    }

    private String submitToEta(EtaItemCode code, AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemCode", code.getItemCode());
            payload.put("codeType", code.getCodeType());
            if (code.getDescription() != null && !code.getDescription().isBlank()) {
                payload.put("description", code.getDescription());
            }
            String body = objectMapper.writeValueAsString(payload);

            String response = restClient.post()
                    .uri(baseUrl + "/codesusage")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                return root.path("id").asText(null);
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                tokenManager.invalidateToken(
                        config.getBranch().getId(), config.getEnvironment());
            }
            throw new RuntimeException(
                    "ETA code registration failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new RuntimeException("ETA code registration error", e);
        }
        return null;
    }

    private void updateOnEta(EtaItemCode code, AuthorityConfig config) {
        String baseUrl = resolveBaseUrl(config.getEnvironment());
        String token = tokenManager.getToken(config);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemCode", code.getItemCode());
            payload.put("codeType", code.getCodeType());
            if (code.getDescription() != null && !code.getDescription().isBlank()) {
                payload.put("description", code.getDescription());
            }
            String body = objectMapper.writeValueAsString(payload);

            restClient.put()
                    .uri(baseUrl + "/codesusage/" + code.getEtaCodeId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                tokenManager.invalidateToken(
                        config.getBranch().getId(), config.getEnvironment());
            }
            throw new RuntimeException(
                    "ETA code update failed: " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new RuntimeException("ETA code update error", e);
        }
    }

    private Optional<AuthorityConfig> resolveEtaConfig(Long companyId) {
        List<AuthorityConfig> configs = authorityConfigRepository
                .findByCompanyIdAndAuthority(companyId, Authority.ETA);
        return configs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .min((a, b) -> {
                    if (a.getEnvironment() == Environment.ETA_PREPRODUCTION) {
                        return -1;
                    }
                    if (b.getEnvironment() == Environment.ETA_PREPRODUCTION) {
                        return 1;
                    }
                    return 0;
                });
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> result = new ArrayList<>();
        array.elements().forEachRemaining(result::add);
        return result;
    }

    private String resolveBaseUrl(Environment environment) {
        return switch (environment) {
          case ETA_PREPRODUCTION ->
              "https://api.preprod.invoicing.eta.gov.eg/api/v1";
          case ETA_PRODUCTION ->
              "https://api.invoicing.eta.gov.eg/api/v1";
          default -> throw new IllegalArgumentException(
                  "Unsupported ETA environment: " + environment);
        };
    }
}
