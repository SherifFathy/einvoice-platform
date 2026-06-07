package com.einvoice.core.util;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Single source of truth for UTC "today" and "this month" boundaries and the
 * half-open {@code [from, to)} time windows used across the Wave 9 operator
 * dashboard KPIs, Admin-Mode system stats, and submission-log date filters.
 *
 * <p>All boundaries are computed in <strong>UTC</strong>, never server-local
 * time (research R3). The persisted timestamps these bounds compare against
 * (e.g. {@code submission_attempts.submitted_at}, ETA header
 * {@code issueDatetime}) are stored as {@link OffsetDateTime} in UTC, so every
 * method returns {@link OffsetDateTime} to bind cleanly to the JPQL predicates
 * {@code column >= :from AND column < :to}.
 *
 * <p>"Today" and "this month" follow the UTC calendar by design: a Cairo/Riyadh
 * operator's "today" is a UTC day, which can differ near midnight. Boundary
 * methods accept an injectable {@link Clock} (defaulting to
 * {@link Clock#systemUTC()}) so acceptance and boundary tests can pin the clock
 * deterministically. The clock's instant is always interpreted in UTC,
 * regardless of the clock's own time-zone.
 *
 * <p>Pure utility: immutable, no Spring, no dependencies beyond {@code java.time}.
 */
public final class UtcDateRange {

    private UtcDateRange() {
    }

    /**
     * Immutable half-open {@code [from, to)} UTC time window.
     *
     * <p>{@code from} is inclusive and {@code to} is exclusive, matching the
     * predicate pattern {@code column >= :from AND column < :to}. Two adjacent
     * windows therefore never double-count an instant on the seam.
     *
     * @param from inclusive lower bound (UTC), never {@code null}
     * @param to   exclusive upper bound (UTC), never {@code null}; must not
     *             precede {@code from}
     */
    public record Range(OffsetDateTime from, OffsetDateTime to) {

        /**
         * Validates the bounds.
         */
        public Range {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            if (to.isBefore(from)) {
                throw new IllegalArgumentException(
                        "Range 'to' must not precede 'from'");
            }
        }
    }

    /**
     * Returns the instant {@code 00:00:00Z} at the start of the current UTC day,
     * using the system UTC clock.
     *
     * @return start of the current UTC day
     */
    public static OffsetDateTime startOfUtcDay() {
        return startOfUtcDay(Clock.systemUTC());
    }

    /**
     * Returns the instant {@code 00:00:00Z} at the start of the current UTC day,
     * resolved from the supplied clock so tests can pin the boundary.
     *
     * @param clock the time source; its instant is interpreted in UTC regardless
     *              of the clock's own time-zone
     * @return start of the current UTC day
     */
    public static OffsetDateTime startOfUtcDay(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return currentUtc(clock).truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Returns the instant {@code 00:00:00Z} on day 1 of the current UTC month,
     * using the system UTC clock.
     *
     * @return start of the current UTC month
     */
    public static OffsetDateTime startOfUtcMonth() {
        return startOfUtcMonth(Clock.systemUTC());
    }

    /**
     * Returns the instant {@code 00:00:00Z} on day 1 of the current UTC month,
     * resolved from the supplied clock so tests can pin the boundary.
     *
     * @param clock the time source; its instant is interpreted in UTC regardless
     *              of the clock's own time-zone
     * @return start of the current UTC month
     */
    public static OffsetDateTime startOfUtcMonth(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return currentUtc(clock).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Returns the half-open UTC window covering the current day:
     * {@code [startOfUtcDay(clock), startOfUtcDay(clock) + 1 day)}.
     *
     * @param clock the time source; its instant is interpreted in UTC
     * @return today's UTC day as a half-open range
     */
    public static Range todayRange(Clock clock) {
        OffsetDateTime start = startOfUtcDay(clock);
        return new Range(start, start.plusDays(1));
    }

    /**
     * Returns the half-open UTC window covering the current month:
     * {@code [startOfUtcMonth(clock), startOfNextUtcMonth)}.
     *
     * @param clock the time source; its instant is interpreted in UTC
     * @return this UTC month as a half-open range
     */
    public static Range thisMonthRange(Clock clock) {
        OffsetDateTime start = startOfUtcMonth(clock);
        return new Range(start, start.plusMonths(1));
    }

    private static OffsetDateTime currentUtc(Clock clock) {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }
}
