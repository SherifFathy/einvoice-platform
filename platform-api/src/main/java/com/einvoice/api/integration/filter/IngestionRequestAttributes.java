package com.einvoice.api.integration.filter;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Carries the resolved (companyId, authorityEnvironmentId) from the ingestion
 * service back up to {@link IngestionPayloadArchiveFilter} via request-scoped
 * attributes. Needed because Spring Security's JwtAuthenticationFilter clears
 * TenantContext in its own {@code finally} block, which runs before the outer
 * archive filter's {@code finally} reads it.
 */
public final class IngestionRequestAttributes {

    public static final String DOC_TYPE_ETA_INVOICE = "ETA_INVOICE";
    public static final String DOC_TYPE_ETA_RECEIPT = "ETA_RECEIPT";
    public static final String DOC_TYPE_ZATCA_STANDARD = "ZATCA_STANDARD";
    public static final String DOC_TYPE_ZATCA_SIMPLIFIED = "ZATCA_SIMPLIFIED";

    static final String COMPANY_ID = "einvoice.gateway.companyId";
    static final String AUTHORITY_ENV_ID = "einvoice.gateway.authorityEnvironmentId";
    static final String DOCUMENT_ID = "einvoice.gateway.documentId";
    static final String DOCUMENT_TYPE = "einvoice.gateway.documentType";

    private IngestionRequestAttributes() {
    }

    /**
     * Called by ingestion services after CompanyResolutionService.resolve() succeeds.
     *
     * @param companyId the resolved company
     * @param authorityEnvironmentId the resolved authority environment
     */
    public static void stash(UUID companyId, Short authorityEnvironmentId) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        attrs.setAttribute(COMPANY_ID, companyId, RequestAttributes.SCOPE_REQUEST);
        attrs.setAttribute(AUTHORITY_ENV_ID, authorityEnvironmentId, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * Called by each ingestion service right after {@code repository.save(header)} succeeds
     * so the archive filter can patch the polymorphic document pointer on the archive row
     * (V63 — closes the archive → document forensic trace gap).
     *
     * @param documentId the persisted document identifier
     * @param documentType the persisted document type discriminator
     */
    public static void stashDocument(UUID documentId, String documentType) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        attrs.setAttribute(DOCUMENT_ID, documentId, RequestAttributes.SCOPE_REQUEST);
        attrs.setAttribute(DOCUMENT_TYPE, documentType, RequestAttributes.SCOPE_REQUEST);
    }

    /** Called by {@link IngestionPayloadArchiveFilter} in its finally block. */
    static UUID readCompanyId(HttpServletRequest request) {
        Object v = request.getAttribute(COMPANY_ID);
        return v instanceof UUID uuid ? uuid : null;
    }

    static Short readAuthorityEnvironmentId(HttpServletRequest request) {
        Object v = request.getAttribute(AUTHORITY_ENV_ID);
        return v instanceof Short s ? s : null;
    }

    static UUID readDocumentId(HttpServletRequest request) {
        Object v = request.getAttribute(DOCUMENT_ID);
        return v instanceof UUID uuid ? uuid : null;
    }

    static String readDocumentType(HttpServletRequest request) {
        Object v = request.getAttribute(DOCUMENT_TYPE);
        return v instanceof String s ? s : null;
    }

    /**
     * Test-only convenience.
     *
     * @return the stashed company id for the current request, or {@code null}
     */
    public static UUID readCompanyIdFromCurrentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : readCompanyId(attrs.getRequest());
    }
}
