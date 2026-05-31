package com.einvoice.core.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.einvoice.core.error.TotalsInconsistentException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EtaMoneyMathTest {

    @Test
    void round5_roundsHalfUp_toFiveDecimals() {
        assertEquals(new BigDecimal("1.23457"), EtaMoneyMath.round5(new BigDecimal("1.234567")));
        assertEquals(new BigDecimal("1.23456"), EtaMoneyMath.round5(new BigDecimal("1.234564")));
        assertEquals(new BigDecimal("0.00001"), EtaMoneyMath.round5(new BigDecimal("0.000005")));
    }

    @Test
    void round5_handlesNullAsZero() {
        assertEquals(BigDecimal.ZERO.setScale(5), EtaMoneyMath.round5(null));
    }

    @Test
    void sum5_addsMultipleValues() {
        BigDecimal result = EtaMoneyMath.sum5(
                new BigDecimal("100.12345"),
                new BigDecimal("200.54321"),
                new BigDecimal("0.00001"));
        assertEquals(new BigDecimal("300.66667"), result);
    }

    @Test
    void sum5_skipsNulls() {
        BigDecimal result = EtaMoneyMath.sum5(
                new BigDecimal("10.00000"), null, new BigDecimal("5.00000"));
        assertEquals(new BigDecimal("15.00000"), result);
    }

    @Test
    void reconcileLineTotal_passesForConsistentValues() {
        EtaMoneyMath.reconcileLineTotal(
                new BigDecimal("1000.00000"),
                new BigDecimal("100.00000"),
                new BigDecimal("50.00000"),
                BigDecimal.ZERO,
                new BigDecimal("25.00000"),
                new BigDecimal("140.00000"),
                new BigDecimal("1015.00000"));
    }

    @Test
    void reconcileLineTotal_throwsForInconsistentValues() {
        assertThrows(TotalsInconsistentException.class, () ->
                EtaMoneyMath.reconcileLineTotal(
                        new BigDecimal("1000.00000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("999.00000")));
    }

    @Test
    void reconcileLineTotal_5linesWithMixedVatAndWht() {
        BigDecimal line1Total = new BigDecimal("1200.00000");
        BigDecimal line2Total = new BigDecimal("600.00000");
        BigDecimal line3Total = new BigDecimal("1150.00000");
        BigDecimal line4Total = new BigDecimal("180.00000");
        BigDecimal line5Total = new BigDecimal("550.00000");

        BigDecimal linesSum = EtaMoneyMath.sum5(line1Total, line2Total, line3Total,
                line4Total, line5Total);

        BigDecimal totalSales = new BigDecimal("3500.00000");
        BigDecimal totalDiscount = new BigDecimal("200.00000");
        BigDecimal itemsDiscount = new BigDecimal("20.00000");
        BigDecimal extraDiscount = new BigDecimal("100.00000");
        BigDecimal netAmount = new BigDecimal("3180.00000");
        BigDecimal totalAmount = new BigDecimal("3680.00000");

        EtaMoneyMath.reconcileHeaderTotals(totalSales, totalDiscount, extraDiscount,
                itemsDiscount, netAmount, totalAmount, linesSum);
    }

    @Test
    void reconcileHeaderTotals_passesWithExtraDiscount() {
        BigDecimal totalSales = new BigDecimal("1000.00000");
        BigDecimal totalDiscount = new BigDecimal("100.00000");
        BigDecimal extraDiscount = new BigDecimal("50.00000");
        BigDecimal itemsDiscount = new BigDecimal("10.00000");
        BigDecimal netAmount = new BigDecimal("840.00000");
        BigDecimal linesSum = new BigDecimal("840.00000");

        EtaMoneyMath.reconcileHeaderTotals(totalSales, totalDiscount, extraDiscount,
                itemsDiscount, netAmount, linesSum, linesSum);
    }

    @Test
    void reconcileHeaderTotals_throwsWhenNetDoesNotReconcile() {
        assertThrows(TotalsInconsistentException.class, () ->
                EtaMoneyMath.reconcileHeaderTotals(
                        new BigDecimal("1000.00000"),
                        new BigDecimal("100.00000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("800.00000"),
                        new BigDecimal("900.00000"),
                        new BigDecimal("900.00000")));
    }

    @Test
    void reconcileHeaderTotals_throwsWhenLinesSumDoesNotMatchTotalAmount() {
        assertThrows(TotalsInconsistentException.class, () ->
                EtaMoneyMath.reconcileHeaderTotals(
                        new BigDecimal("1000.00000"),
                        new BigDecimal("100.00000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("900.00000"),
                        new BigDecimal("900.00000"),
                        new BigDecimal("950.00000")));
    }
}
