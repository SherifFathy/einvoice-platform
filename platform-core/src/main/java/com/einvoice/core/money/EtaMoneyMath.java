package com.einvoice.core.money;

import com.einvoice.core.error.TotalsInconsistentException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** Javadoc. */
public final class EtaMoneyMath {

    public static final int SCALE = 5;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final MathContext MC = new MathContext(SCALE + 10, ROUNDING_MODE);

    private EtaMoneyMath() {
    }

    /**
     * Round a value to 5 decimal places, HALF_UP. Null is treated as zero.
     *
     * @param value the value to round
     * @return the rounded value
     */
    public static BigDecimal round5(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        }
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Sum a list of values at 5-decimal precision. Null entries are skipped.
     *
     * @param values the values to sum
     * @return the 5-decimal sum
     */
    public static BigDecimal sum5(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
        for (BigDecimal v : values) {
            if (v != null) {
                total = total.add(round5(v));
            }
        }
        return round5(total);
    }

    /**
     * Reconcile a line's expected total against its components.
     *
     * @param salesTotal       gross sales total for the line
     * @param discountAmount   line-level discount
     * @param itemsDiscount    item-level discount
     * @param valueDifference  value difference adjustment
     * @param totalTaxableFees taxable fees
     * @param taxAmount        total tax amount on the line
     * @param expectedTotal    the expected line total to verify
     */
    public static void reconcileLineTotal(BigDecimal salesTotal,
            BigDecimal discountAmount, BigDecimal itemsDiscount,
            BigDecimal valueDifference, BigDecimal totalTaxableFees,
            BigDecimal taxAmount, BigDecimal expectedTotal) {
        BigDecimal computed = round5(salesTotal)
                .subtract(round5(discountAmount))
                .subtract(round5(itemsDiscount))
                .add(round5(valueDifference))
                .add(round5(totalTaxableFees))
                .add(round5(taxAmount));
        if (round5(computed).compareTo(round5(expectedTotal)) != 0) {
            throw new TotalsInconsistentException(
                    "Line total does not reconcile",
                    "lineTotal", round5(expectedTotal).toPlainString(), round5(computed).toPlainString());
        }
    }

    /**
     * Reconcile header totals against line sums.
     * netAmount = totalSalesAmount - totalDiscountAmount - extraDiscountAmount
     *     - totalItemsDiscountAmount.
     * totalAmount = netAmount + sum(line.taxAmount) (the line-total check below
     *     already verifies totalAmount == sum(line.total) which includes tax).
     * Raw-BigDecimal API chosen because entity types EtaInvoiceHeader / lines
     * are not available until Phase 3A (T039-T041). Phase 3D (T056) will add
     * entity-typed convenience overloads.
     *
     * @param totalSalesAmount         header total sales amount
     * @param totalDiscountAmount      total line-discount amount
     * @param extraDiscountAmount      extra discount applied at header level
     * @param totalItemsDiscountAmount total items-discount amount
     * @param netAmount                expected header net amount
     * @param totalAmount              expected header total amount
     * @param linesTotalSum            sum of all line totals
     */
    public static void reconcileHeaderTotals(BigDecimal totalSalesAmount,
            BigDecimal totalDiscountAmount, BigDecimal extraDiscountAmount,
            BigDecimal totalItemsDiscountAmount, BigDecimal netAmount,
            BigDecimal totalAmount, BigDecimal linesTotalSum) {
        BigDecimal computedNet = round5(totalSalesAmount)
                .subtract(round5(totalDiscountAmount))
                .subtract(round5(extraDiscountAmount))
                .subtract(round5(totalItemsDiscountAmount));
        if (round5(computedNet).compareTo(round5(netAmount)) != 0) {
            throw new TotalsInconsistentException(
                    "Header netAmount does not reconcile",
                    "netAmount", round5(netAmount).toPlainString(),
                    round5(computedNet).toPlainString());
        }
        if (round5(linesTotalSum).compareTo(round5(totalAmount)) != 0) {
            throw new TotalsInconsistentException(
                    "Header totalAmount does not match sum of line totals",
                    "totalAmount", round5(totalAmount).toPlainString(),
                    round5(linesTotalSum).toPlainString());
        }
    }
}
