package com.einvoice.core.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Javadoc. */
public final class ZatcaMoneyMath {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    public static final MathContext MC = new MathContext(SCALE + 10, ROUNDING_MODE);

    private ZatcaMoneyMath() {
    }

    /**
     * Round a value to 2 decimal places, HALF_EVEN. Null is treated as zero.
     *
     * @param value the value to round
     * @return the rounded value
     */
    public static BigDecimal round2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Sum a list of values at 2-decimal precision. Null entries are skipped.
     *
     * @param values the values to sum
     * @return the 2-decimal sum
     */
    public static BigDecimal sum2(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        for (BigDecimal v : values) {
            if (v != null) {
                total = total.add(round2(v));
            }
        }
        return round2(total);
    }
}
