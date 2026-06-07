package com.einvoice.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UtcDateRangeTest {

    @Test
    void startOfUtcDayReturnsMidnightUtc() {
        Clock clock = fixed("2026-06-07T13:45:30.123Z");
        assertEquals(OffsetDateTime.parse("2026-06-07T00:00:00Z"),
                UtcDateRange.startOfUtcDay(clock));
    }

    @Test
    void startOfUtcDayTruncatesJustBeforeDayRollover() {
        // One nanosecond before midnight UTC still belongs to today.
        Clock clock = fixed("2026-06-07T23:59:59.999999999Z");
        assertEquals(OffsetDateTime.parse("2026-06-07T00:00:00Z"),
                UtcDateRange.startOfUtcDay(clock));
    }

    @Test
    void startOfUtcDayIgnoresClockTimeZoneAndUsesUtc() {
        // Same instant, clock zone set to Asia/Riyadh (+03): boundary is UTC.
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T23:30:00Z"),
                ZoneId.of("Asia/Riyadh"));
        assertEquals(OffsetDateTime.parse("2026-06-07T00:00:00Z"),
                UtcDateRange.startOfUtcDay(clock));
    }

    @Test
    void startOfUtcMonthReturnsFirstDayMidnightUtc() {
        Clock clock = fixed("2026-06-15T08:30:00Z");
        assertEquals(OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                UtcDateRange.startOfUtcMonth(clock));
    }

    @Test
    void startOfUtcMonthAtFirstInstantOfNewMonth() {
        // First nanosecond past midnight of a new month → start of that month.
        Clock clock = fixed("2026-07-01T00:00:00.001Z");
        assertEquals(OffsetDateTime.parse("2026-07-01T00:00:00Z"),
                UtcDateRange.startOfUtcMonth(clock));
    }

    @Test
    void startOfUtcMonthAtLastDayOfMonth() {
        Clock clock = fixed("2026-01-31T23:59:59Z");
        assertEquals(OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                UtcDateRange.startOfUtcMonth(clock));
    }

    @Test
    void todayRangeIsHalfOpenOneDayWindow() {
        Clock clock = fixed("2026-06-07T13:45:30Z");
        UtcDateRange.Range range = UtcDateRange.todayRange(clock);
        assertEquals(OffsetDateTime.parse("2026-06-07T00:00:00Z"), range.from());
        assertEquals(OffsetDateTime.parse("2026-06-08T00:00:00Z"), range.to());
    }

    @Test
    void thisMonthRangeIsHalfOpenOneMonthWindow() {
        Clock clock = fixed("2026-02-10T12:00:00Z");
        UtcDateRange.Range range = UtcDateRange.thisMonthRange(clock);
        assertEquals(OffsetDateTime.parse("2026-02-01T00:00:00Z"), range.from());
        assertEquals(OffsetDateTime.parse("2026-03-01T00:00:00Z"), range.to());
    }

    @Test
    void thisMonthRangeHandlesYearBoundary() {
        Clock clock = fixed("2026-12-31T23:59:59Z");
        UtcDateRange.Range range = UtcDateRange.thisMonthRange(clock);
        assertEquals(OffsetDateTime.parse("2026-12-01T00:00:00Z"), range.from());
        assertEquals(OffsetDateTime.parse("2027-01-01T00:00:00Z"), range.to());
    }

    @Test
    void startOfUtcDayNoArgReturnsMidnightUtc() {
        OffsetDateTime start = UtcDateRange.startOfUtcDay();
        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(0, start.getNano());
        assertEquals(ZoneOffset.UTC, start.getOffset());
    }

    @Test
    void rangeRejectsNullBounds() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-07T00:00:00Z");
        assertThrows(NullPointerException.class,
                () -> new UtcDateRange.Range(now, null));
        assertThrows(NullPointerException.class,
                () -> new UtcDateRange.Range(null, now));
    }

    @Test
    void rangeRejectsToBeforeFrom() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-08T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-07T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new UtcDateRange.Range(from, to));
    }

    private static Clock fixed(String instantIso) {
        return Clock.fixed(Instant.parse(instantIso), ZoneOffset.UTC);
    }
}
