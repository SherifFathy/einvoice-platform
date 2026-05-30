package com.einvoice.api.integration.filter;

import com.einvoice.api.integration.service.InboundPayloadArchiveService;
import com.einvoice.core.error.InboundPayloadArchiveException;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Archives every inbound integration request before passing it down the filter chain (FR-OBS-003). */
public class IngestionPayloadArchiveFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IngestionPayloadArchiveFilter.class);
    private static final String INTEGRATION_PATH_PREFIX = "/api/integration/v1/";

    private final InboundPayloadArchiveService archiveService;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a new IngestionPayloadArchiveFilter.
     *
     * @param archiveService the archive service
     * @param objectMapper the object mapper
     */
    public IngestionPayloadArchiveFilter(InboundPayloadArchiveService archiveService,
            ObjectMapper objectMapper) {
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTEGRATION_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, bodyBytes);

        long t0 = System.nanoTime();

        UUID payloadArchiveId;
        try {
            payloadArchiveId = archiveService.archive(request.getRequestURI(), bodyBytes);
        } catch (InboundPayloadArchiveException e) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"ARCHIVE_WRITE_FAILED\","
                    + "\"message\":\"Inbound payload archive unavailable; ingestion aborted.\","
                    + "\"details\":{\"cause\":\"storage unavailable\"}}");
            return;
        }

        MDC.put("payloadArchiveId", payloadArchiveId.toString());
        MDC.put("endpoint", request.getRequestURI());

        try {
            JsonNode tree = objectMapper.readTree(bodyBytes);
            if (tree.has("companyRegistrationNumber")) {
                MDC.put("companyTaxNumber", tree.get("companyRegistrationNumber").asText());
            }
            if (tree.has("environment")) {
                MDC.put("environment", tree.get("environment").asText());
            }
        } catch (Exception ignored) {
        }

        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long latencyMs = (System.nanoTime() - t0) / 1_000_000;
            MDC.put("outcome", String.valueOf(response.getStatus()));
            MDC.put("latencyMs", String.valueOf(latencyMs));

            UUID companyId = IngestionRequestAttributes.readCompanyId(request);
            Short authorityEnvId = IngestionRequestAttributes.readAuthorityEnvironmentId(request);
            if (companyId == null) {
                TenantContext.Holder ctx = TenantContext.current();
                if (ctx != null) {
                    companyId = ctx.companyId();
                    authorityEnvId = ctx.authorityEnvironmentId();
                }
            }
            UUID documentId = IngestionRequestAttributes.readDocumentId(request);
            String documentType = IngestionRequestAttributes.readDocumentType(request);
            archiveService.patchOutcome(payloadArchiveId, response.getStatus(), companyId, authorityEnvId,
                    documentId, documentType);

            log.info("Ingestion request completed");
            TenantContext.clear();
            MDC.clear();
        }
    }
}
