package com.einvoice.core.service;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Centralized calculation engine for invoice line amounts, VAT breakdown, and document totals. */
@Service
public class InvoiceCalculationService {

    private static final int CALC_SCALE = 4;
    private static final int FINAL_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /**
     * Recalculates all line amounts and document-level totals in place.
     *
     * @param invoice the invoice to recalculate
     */
    public void recalculate(Invoice invoice) {
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            invoice.setTotalLineNet(BigDecimal.ZERO);
            invoice.setTotalAllowances(BigDecimal.ZERO);
            invoice.setTotalWithoutVat(BigDecimal.ZERO);
            invoice.setTotalVat(BigDecimal.ZERO);
            invoice.setTotalWithVat(BigDecimal.ZERO);
            invoice.setAmountDue(BigDecimal.ZERO);
            invoice.getVatBreakdown().clear();
            return;
        }

        BigDecimal totalLineNet = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            calculateLine(line);
            totalLineNet = totalLineNet.add(line.getLineNetAmount());
        }

        invoice.setTotalLineNet(totalLineNet.setScale(FINAL_SCALE, RoundingMode.HALF_UP));

        BigDecimal totalAllowances = invoice.getTotalAllowances() != null
                ? invoice.getTotalAllowances() : BigDecimal.ZERO;
        BigDecimal totalWithoutVat = totalLineNet.subtract(totalAllowances)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        invoice.setTotalWithoutVat(totalWithoutVat);

        List<InvoiceVatBreakdown> breakdown = buildVatBreakdown(
                invoice, lines, totalLineNet, totalAllowances);
        invoice.getVatBreakdown().clear();
        invoice.getVatBreakdown().addAll(breakdown);

        BigDecimal totalVat = breakdown.stream()
                .map(InvoiceVatBreakdown::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        invoice.setTotalVat(totalVat);

        BigDecimal totalWithVat = totalWithoutVat.add(totalVat)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        invoice.setTotalWithVat(totalWithVat);

        BigDecimal prepaid = invoice.getPrepaidAmount() != null
                ? invoice.getPrepaidAmount() : BigDecimal.ZERO;
        BigDecimal amountDue = totalWithVat.subtract(prepaid)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        invoice.setAmountDue(amountDue);
    }

    /**
     * Calculates a single line's net, VAT, and total amounts.
     *
     * @param line the invoice line to calculate
     */
    public void calculateLine(InvoiceLine line) {
        BigDecimal gross = line.getUnitPrice().multiply(line.getQuantity())
                .setScale(CALC_SCALE, RoundingMode.HALF_UP);
        BigDecimal discount = line.getDiscountAmount() != null
                ? line.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal lineNet = gross.subtract(discount)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
        line.setLineNetAmount(lineNet);

        BigDecimal vatRate = line.getVatRate() != null
                ? line.getVatRate() : BigDecimal.ZERO;
        BigDecimal lineVat = lineNet.multiply(vatRate)
                .divide(ONE_HUNDRED, FINAL_SCALE, RoundingMode.HALF_UP);
        line.setLineVatAmount(lineVat);

        line.setLineTotal(lineNet.add(lineVat)
                .setScale(FINAL_SCALE, RoundingMode.HALF_UP));
    }

    private List<InvoiceVatBreakdown> buildVatBreakdown(Invoice invoice,
            List<InvoiceLine> lines, BigDecimal totalLineNet,
            BigDecimal totalAllowances) {
        Map<String, InvoiceVatBreakdown> map = new LinkedHashMap<>();
        for (InvoiceLine line : lines) {
            String key = line.getVatCategory() + "@" + line.getVatRate();
            map.computeIfAbsent(key, k -> InvoiceVatBreakdown.builder()
                    .invoice(invoice)
                    .vatCategoryCode(line.getVatCategory())
                    .vatRate(line.getVatRate())
                    .taxableAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .build());
            InvoiceVatBreakdown bd = map.get(key);
            bd.setTaxableAmount(bd.getTaxableAmount()
                    .add(line.getLineNetAmount()));
        }

        List<InvoiceVatBreakdown> result = new ArrayList<>(map.values());

        boolean hasAllowance = totalAllowances != null
                && totalAllowances.signum() > 0
                && totalLineNet != null
                && totalLineNet.signum() > 0;
        if (hasAllowance) {
            BigDecimal remainingAllowance = totalAllowances;
            for (int i = 0; i < result.size(); i++) {
                InvoiceVatBreakdown bd = result.get(i);
                BigDecimal share;
                if (i == result.size() - 1) {
                    share = remainingAllowance;
                } else {
                    share = bd.getTaxableAmount()
                            .multiply(totalAllowances)
                            .divide(totalLineNet, CALC_SCALE, RoundingMode.HALF_UP);
                    remainingAllowance = remainingAllowance.subtract(share);
                }
                bd.setTaxableAmount(bd.getTaxableAmount().subtract(share));
            }
        }

        for (InvoiceVatBreakdown bd : result) {
            BigDecimal taxable = bd.getTaxableAmount()
                    .setScale(FINAL_SCALE, RoundingMode.HALF_UP);
            BigDecimal rate = bd.getVatRate() != null
                    ? bd.getVatRate() : BigDecimal.ZERO;
            BigDecimal tax = taxable.multiply(rate)
                    .divide(ONE_HUNDRED, FINAL_SCALE, RoundingMode.HALF_UP);
            bd.setTaxableAmount(taxable);
            bd.setTaxAmount(tax);
        }
        return result;
    }
}
