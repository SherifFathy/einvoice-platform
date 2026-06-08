package com.einvoice.core.service.dashboard;

import com.einvoice.core.service.dashboard.DashboardReadModels.CertificateStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Derives the dashboard certificate-expiry status from a ZATCA signing
 * certificate's expiry date. Boundaries are computed against the UTC calendar
 * (research R3) using the injectable {@link Clock}. Only the derived
 * {@code daysRemaining} / {@code expiringSoon} / {@code expired} flags are
 * produced; certificate bytes and key material are never handled here
 * (Constitution VI/XVIII). ETA environments carry no signing certificate, so
 * callers pass a {@code null} expiry and receive {@code null}.
 */
@Component
public class CertificateExpiryEvaluator {

    /**
     * A certificate is flagged "expiring soon" when it expires within this many
     * days (FR-003).
     */
    public static final int EXPIRING_SOON_THRESHOLD_DAYS = 30;

    private final Clock clock;

    /**
     * Constructs the evaluator with the UTC clock used for "today".
     *
     * @param clock the time source; its instant is interpreted in UTC
     */
    public CertificateExpiryEvaluator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Evaluates the expiry status for a certificate expiry date.
     *
     * @param certificateExpiryDate the ZATCA certificate expiry, or {@code null}
     *                              when the company has no signing certificate
     * @return the derived status, or {@code null} when the expiry is absent
     */
    public CertificateStatus evaluate(LocalDate certificateExpiryDate) {
        if (certificateExpiryDate == null) {
            return null;
        }
        LocalDate todayUtc = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        int daysRemaining = (int) ChronoUnit.DAYS.between(todayUtc, certificateExpiryDate);
        boolean expiringSoon =
                daysRemaining >= 0 && daysRemaining < EXPIRING_SOON_THRESHOLD_DAYS;
        boolean expired = daysRemaining < 0;
        return new CertificateStatus(daysRemaining, expiringSoon, expired);
    }
}
