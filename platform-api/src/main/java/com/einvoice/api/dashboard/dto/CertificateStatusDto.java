package com.einvoice.api.dashboard.dto;

/**
 * Derived certificate-expiry status. Carries no certificate or key material.
 *
 * @param daysRemaining expiry minus today (UTC); negative when already expired
 * @param expiringSoon {@code true} when {@code 0 <= daysRemaining < 30}
 * @param expired {@code true} when {@code daysRemaining < 0}
 */
public record CertificateStatusDto(
        int daysRemaining,
        boolean expiringSoon,
        boolean expired) {
}
