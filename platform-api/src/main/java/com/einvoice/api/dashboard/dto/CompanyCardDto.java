package com.einvoice.api.dashboard.dto;

import java.util.UUID;

/**
 * One company card on the operator dashboard.
 *
 * @param companyId owning company
 * @param nameEn English company name
 * @param nameAr Arabic company name
 * @param taxNumber company tax number
 * @param active whether the company is active
 * @param pendingCount documents in {@code SUBMITTING/SUBMITTED/IN_REVIEW}
 * @param failedCount rejected documents plus documents whose latest
 *                    submission attempt ended in {@code ERROR/TIMEOUT}
 * @param certificate certificate status, or {@code null} when the company
 *                    has no signing certificate (ETA)
 */
public record CompanyCardDto(
        UUID companyId,
        String nameEn,
        String nameAr,
        String taxNumber,
        boolean active,
        int pendingCount,
        int failedCount,
        CertificateStatusDto certificate) {
}
