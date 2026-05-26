package com.einvoice.core.money;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZatcaMoneyMathTest {

    @Test
    void round2HalfEven005RoundsTo00() {
        // Banker's rounding: 0.005 → 0.00 (round to even)
        assertEquals(new BigDecimal("0.00"),
                ZatcaMoneyMath.round2(new BigDecimal("0.005")));
    }

    @Test
    void round2HalfEven015RoundsTo02() {
        // Banker's rounding: 0.015 → 0.02 (round to even)
        assertEquals(new BigDecimal("0.02"),
                ZatcaMoneyMath.round2(new BigDecimal("0.015")));
    }

    @Test
    void round2HalfEven025RoundsTo02() {
        // Banker's rounding: 0.025 → 0.02 (round to even)
        assertEquals(new BigDecimal("0.02"),
                ZatcaMoneyMath.round2(new BigDecimal("0.025")));
    }

    @Test
    void round2HalfEven035RoundsTo04() {
        // Banker's rounding: 0.035 → 0.04 (round to even)
        assertEquals(new BigDecimal("0.04"),
                ZatcaMoneyMath.round2(new BigDecimal("0.035")));
    }

    @Test
    void round2NullReturnsZero() {
        assertEquals(new BigDecimal("0.00"), ZatcaMoneyMath.round2(null));
    }

    @Test
    void sum2AddsCorrectly() {
        BigDecimal result = ZatcaMoneyMath.sum2(
                new BigDecimal("10.005"),
                new BigDecimal("20.015"),
                new BigDecimal("30.025"));
        assertEquals(new BigDecimal("60.04"), result);
    }

    @Test
    void sum2SkipsNulls() {
        BigDecimal result = ZatcaMoneyMath.sum2(
                new BigDecimal("10.01"), null, new BigDecimal("20.02"));
        assertEquals(new BigDecimal("30.03"), result);
    }

    @Test
    void round2TruncatesScale() {
        assertEquals(new BigDecimal("1.23"),
                ZatcaMoneyMath.round2(new BigDecimal("1.234")));
        assertEquals(new BigDecimal("1.24"),
                ZatcaMoneyMath.round2(new BigDecimal("1.235")));
    }
}
