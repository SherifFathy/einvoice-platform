package com.einvoice.zatca.build;

import com.einvoice.core.domain.zatca.ZatcaSimplifiedAllowance;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedLine;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedTaxSubtotal;
import com.einvoice.core.domain.zatca.ZatcaStandardAllowance;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import com.einvoice.core.domain.zatca.ZatcaStandardTaxSubtotal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds ZATCA-compliant UBL XML for standard and simplified invoices. */
@Component
public class ZatcaUblBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Build UBL XML for a standard (B2B) invoice.
     *
     * @param header the standard invoice header
     * @return UTF-8 encoded UBL XML bytes
     */
    public byte[] buildStandardUbl(ZatcaStandardHeader header) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:Invoice-2\" "
                + "xmlns:cac=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:CommonAggregateComponents-2\" "
                + "xmlns:cbc=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:CommonBasicComponents-2\" "
                + "xmlns:ext=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:CommonExtensionComponents-2\">");

        sb.append("<cbc:ProfileID>").append(esc(
                header.getBusinessProcessCode() != null
                        ? header.getBusinessProcessCode()
                        : "reporting:1.0"))
                .append("</cbc:ProfileID>");

        sb.append("<cbc:ID>").append(esc(header.getInvoiceNumber()))
                .append("</cbc:ID>");
        sb.append("<cbc:UUID>").append(esc(header.getId() != null
                        ? header.getId().toString() : UUID.randomUUID()
                        .toString()))
                .append("</cbc:UUID>");
        sb.append("<cbc:IssueDate>").append(
                header.getIssueDate().format(DATE_FMT))
                .append("</cbc:IssueDate>");
        sb.append("<cbc:IssueTime>").append(
                header.getIssueTime().format(TIME_FMT))
                .append("</cbc:IssueTime>");
        sb.append("<cbc:InvoiceTypeCode name=\"")
                .append(esc(header.getTransactionTypeCode()))
                .append("\">")
                .append(esc(header.getInvoiceTypeCode()))
                .append("</cbc:InvoiceTypeCode>");
        sb.append("<cbc:DocumentCurrencyCode>")
                .append(esc(header.getCurrency()))
                .append("</cbc:DocumentCurrencyCode>");
        sb.append("<cbc:TaxCurrencyCode>")
                .append(esc(header.getTaxCurrency()))
                .append("</cbc:TaxCurrencyCode>");

        if (header.getIssuanceReason() != null
                && !header.getIssuanceReason().isBlank()) {
            sb.append("<cbc:Note>").append(esc(header.getIssuanceReason()))
                    .append("</cbc:Note>");
        }

        appendPartyFromPromoted("AccountingSupplierParty", header, true,
                sb);
        if (header.getBuyerData() != null
                && !header.getBuyerData().isEmpty()) {
            appendPartyFromPromoted("AccountingCustomerParty", header,
                    false, sb);
        }

        if (header.getBillingReferenceId() != null
                && !header.getBillingReferenceId().isBlank()) {
            sb.append("<cac:BillingReference>");
            sb.append("<cac:InvoiceDocumentReference>");
            sb.append("<cbc:ID>")
                    .append(esc(header.getBillingReferenceId()))
                    .append("</cbc:ID>");
            sb.append("</cac:InvoiceDocumentReference>");
            sb.append("</cac:BillingReference>");
        } else if (header.getOriginalInvoiceId() != null) {
            sb.append("<cac:BillingReference>");
            sb.append("<cac:InvoiceDocumentReference>");
            sb.append("<cbc:ID>")
                    .append(header.getOriginalInvoiceId())
                    .append("</cbc:ID>");
            sb.append("</cac:InvoiceDocumentReference>");
            sb.append("</cac:BillingReference>");
        }

        if (header.getPaymentMeansCode() != null) {
            sb.append("<cac:PaymentMeans>");
            sb.append("<cbc:PaymentMeansCode>")
                    .append(esc(header.getPaymentMeansCode()))
                    .append("</cbc:PaymentMeansCode>");
            if (header.getPaymentMeansText() != null) {
                sb.append("<cbc:InstructionNote>")
                        .append(esc(header.getPaymentMeansText()))
                        .append("</cbc:InstructionNote>");
            }
            sb.append("</cac:PaymentMeans>");
        }

        if (header.getAllowances() != null) {
            for (ZatcaStandardAllowance a : header.getAllowances()) {
                appendDocumentAllowance(a.getAmount(), a.getBaseAmount(),
                        a.getPercentage(), a.getReason(), a.getReasonCode(),
                        a.getVatCategoryCode(), a.getVatRate(),
                        header.getCurrency(), sb);
            }
        }

        sb.append("<cac:LegalMonetaryTotal>");
        appendAmount("cbc:LineExtensionAmount",
                header.getLineExtensionAmount(),
                header.getCurrency(), sb);
        appendAmount("cbc:TaxExclusiveAmount",
                header.getTaxExclusiveAmount(),
                header.getCurrency(), sb);
        appendAmount("cbc:TaxInclusiveAmount",
                header.getTaxInclusiveAmount(),
                header.getCurrency(), sb);
        appendAmount("cbc:AllowanceTotalAmount",
                header.allowanceTotal(),
                header.getCurrency(), sb);
        appendAmount("cbc:PrepaidAmount", header.getPrepaidAmount(),
                header.getCurrency(), sb);
        if (header.getRoundingAmount() != null
                && header.getRoundingAmount()
                        .compareTo(BigDecimal.ZERO) != 0) {
            appendAmount("cbc:PayableRoundingAmount",
                    header.getRoundingAmount(),
                    header.getCurrency(), sb);
        }
        appendAmount("cbc:PayableAmount", header.getPayableAmount(),
                header.getCurrency(), sb);
        sb.append("</cac:LegalMonetaryTotal>");

        if (header.getTaxAmount() != null
                && header.getTaxAmount()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:TaxTotal>");
            appendAmount("cbc:TaxAmount", header.getTaxAmount(),
                    header.getCurrency(), sb);
            for (Map.Entry<String, ZatcaStandardTaxSubtotal> e
                    : dedupStandardSubtotals(header.getTaxSubtotals())
                            .entrySet()) {
                ZatcaStandardTaxSubtotal s = e.getValue();
                appendTaxSubtotal(s.getTaxableAmount(), s.getTaxAmount(),
                        s.getVatCategoryCode(), s.getVatRate(),
                        s.getExemptionReasonCode(),
                        s.getExemptionReasonText(),
                        header.getCurrency(), sb);
            }
            sb.append("</cac:TaxTotal>");
        }

        if (header.getTaxAmountAccountingCurrency() != null
                && header.getTaxAmountAccountingCurrency()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:TaxTotal>");
            appendAmountWithCurrency("cbc:TaxAmount",
                    header.getTaxAmountAccountingCurrency(), "SAR", sb);
            sb.append("</cac:TaxTotal>");
        }

        if (header.getLines() != null) {
            for (ZatcaStandardLine line : header.getLines()) {
                appendLine(line, header.getCurrency(), sb);
            }
        }

        sb.append("</Invoice>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Build UBL XML for a simplified (B2C) invoice.
     *
     * @param header the simplified invoice header
     * @return UTF-8 encoded UBL XML bytes
     */
    public byte[] buildSimplifiedUbl(ZatcaSimplifiedHeader header) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:Invoice-2\" "
                + "xmlns:cac=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:CommonAggregateComponents-2\" "
                + "xmlns:cbc=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:CommonBasicComponents-2\" "
                + "xmlns:ext=\"urn:oasis:names:specification:ubl:"
                + "schema:xsd:CommonExtensionComponents-2\">");

        sb.append("<cbc:ProfileID>").append(esc(
                header.getBusinessProcessCode() != null
                        ? header.getBusinessProcessCode()
                        : "reporting:1.0"))
                .append("</cbc:ProfileID>");

        sb.append("<cbc:ID>").append(esc(header.getInvoiceNumber()))
                .append("</cbc:ID>");
        sb.append("<cbc:UUID>").append(esc(header.getId() != null
                        ? header.getId().toString() : UUID.randomUUID()
                        .toString()))
                .append("</cbc:UUID>");
        sb.append("<cbc:IssueDate>").append(
                header.getIssueDate().format(DATE_FMT))
                .append("</cbc:IssueDate>");
        sb.append("<cbc:IssueTime>").append(
                header.getIssueTime().format(TIME_FMT))
                .append("</cbc:IssueTime>");
        sb.append("<cbc:InvoiceTypeCode name=\"")
                .append(esc(header.getTransactionTypeCode()))
                .append("\">")
                .append(esc(header.getInvoiceTypeCode()))
                .append("</cbc:InvoiceTypeCode>");
        sb.append("<cbc:DocumentCurrencyCode>")
                .append(esc(header.getCurrency()))
                .append("</cbc:DocumentCurrencyCode>");
        sb.append("<cbc:TaxCurrencyCode>")
                .append(esc(header.getTaxCurrency()))
                .append("</cbc:TaxCurrencyCode>");

        if (header.getIssuanceReason() != null
                && !header.getIssuanceReason().isBlank()) {
            sb.append("<cbc:Note>").append(esc(header.getIssuanceReason()))
                    .append("</cbc:Note>");
        }

        appendSimplifiedPartyFromPromoted("AccountingSupplierParty", header,
                true, sb);
        if (header.getBuyerData() != null
                && !header.getBuyerData().isEmpty()) {
            appendSimplifiedPartyFromPromoted("AccountingCustomerParty",
                    header, false, sb);
        }

        if (header.getBillingReferenceId() != null
                && !header.getBillingReferenceId().isBlank()) {
            sb.append("<cac:BillingReference>");
            sb.append("<cac:InvoiceDocumentReference>");
            sb.append("<cbc:ID>")
                    .append(esc(header.getBillingReferenceId()))
                    .append("</cbc:ID>");
            sb.append("</cac:InvoiceDocumentReference>");
            sb.append("</cac:BillingReference>");
        } else if (header.getOriginalInvoiceId() != null) {
            sb.append("<cac:BillingReference>");
            sb.append("<cac:InvoiceDocumentReference>");
            sb.append("<cbc:ID>")
                    .append(header.getOriginalInvoiceId())
                    .append("</cbc:ID>");
            sb.append("</cac:InvoiceDocumentReference>");
            sb.append("</cac:BillingReference>");
        }

        if (header.getPaymentMeansCode() != null) {
            sb.append("<cac:PaymentMeans>");
            sb.append("<cbc:PaymentMeansCode>")
                    .append(esc(header.getPaymentMeansCode()))
                    .append("</cbc:PaymentMeansCode>");
            if (header.getPaymentMeansText() != null) {
                sb.append("<cbc:InstructionNote>")
                        .append(esc(header.getPaymentMeansText()))
                        .append("</cbc:InstructionNote>");
            }
            sb.append("</cac:PaymentMeans>");
        }

        if (header.getAllowances() != null) {
            for (ZatcaSimplifiedAllowance a : header.getAllowances()) {
                appendDocumentAllowance(a.getAmount(), a.getBaseAmount(),
                        a.getPercentage(), a.getReason(), a.getReasonCode(),
                        a.getVatCategoryCode(), a.getVatRate(),
                        header.getCurrency(), sb);
            }
        }

        sb.append("<cac:LegalMonetaryTotal>");
        appendAmount("cbc:LineExtensionAmount",
                header.getLineExtensionAmount(),
                header.getCurrency(), sb);
        appendAmount("cbc:TaxExclusiveAmount",
                header.getTaxExclusiveAmount(),
                header.getCurrency(), sb);
        appendAmount("cbc:TaxInclusiveAmount",
                header.getTaxInclusiveAmount(),
                header.getCurrency(), sb);
        appendAmount("cbc:AllowanceTotalAmount",
                header.allowanceTotal(),
                header.getCurrency(), sb);
        appendAmount("cbc:PrepaidAmount", header.getPrepaidAmount(),
                header.getCurrency(), sb);
        if (header.getRoundingAmount() != null
                && header.getRoundingAmount()
                        .compareTo(BigDecimal.ZERO) != 0) {
            appendAmount("cbc:PayableRoundingAmount",
                    header.getRoundingAmount(),
                    header.getCurrency(), sb);
        }
        appendAmount("cbc:PayableAmount", header.getPayableAmount(),
                header.getCurrency(), sb);
        sb.append("</cac:LegalMonetaryTotal>");

        if (header.getTaxAmount() != null
                && header.getTaxAmount()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:TaxTotal>");
            appendAmount("cbc:TaxAmount", header.getTaxAmount(),
                    header.getCurrency(), sb);
            for (Map.Entry<String, ZatcaSimplifiedTaxSubtotal> e
                    : dedupSimplifiedSubtotals(header.getTaxSubtotals())
                            .entrySet()) {
                ZatcaSimplifiedTaxSubtotal s = e.getValue();
                appendTaxSubtotal(s.getTaxableAmount(), s.getTaxAmount(),
                        s.getVatCategoryCode(), s.getVatRate(),
                        s.getExemptionReasonCode(),
                        s.getExemptionReasonText(),
                        header.getCurrency(), sb);
            }
            sb.append("</cac:TaxTotal>");
        }

        if (header.getTaxAmountAccountingCurrency() != null
                && header.getTaxAmountAccountingCurrency()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:TaxTotal>");
            appendAmountWithCurrency("cbc:TaxAmount",
                    header.getTaxAmountAccountingCurrency(), "SAR", sb);
            sb.append("</cac:TaxTotal>");
        }

        if (header.getLines() != null) {
            for (ZatcaSimplifiedLine line : header.getLines()) {
                appendSimplifiedLine(line, header.getCurrency(), sb);
            }
        }

        sb.append("</Invoice>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendProfile(ZatcaStandardHeader header,
            StringBuilder sb) {
        sb.append("<cbc:ProfileID>").append(esc(
                header.getBusinessProcessCode() != null
                        ? header.getBusinessProcessCode()
                        : "reporting:1.0"))
                .append("</cbc:ProfileID>");
    }

    private void appendPartyFromPromoted(String wrapper,
            ZatcaStandardHeader header, boolean isSeller,
            StringBuilder sb) {
        sb.append("<cac:").append(wrapper).append(">");
        sb.append("<cac:Party>");
        String vatNumber = isSeller ? header.getSellerVatNumber()
                : header.getBuyerVatNumber();
        if (vatNumber != null) {
            sb.append("<cac:PartyIdentification>");
            sb.append("<cbc:ID>").append(esc(vatNumber))
                    .append("</cbc:ID>");
            sb.append("</cac:PartyIdentification>");
        }
        sb.append("<cac:PartyName>");
        Map<String, Object> partyData = isSeller
                ? header.getSellerData() : header.getBuyerData();
        Object name = partyData != null
                ? partyData.getOrDefault("partyName",
                        partyData.get("nameEn")) : null;
        sb.append("<cbc:Name>")
                .append(esc(name != null ? name.toString() : ""))
                .append("</cbc:Name>");
        sb.append("</cac:PartyName>");
        sb.append("<cac:PostalAddress>");
        if (partyData != null) {
            appendIfPresent(partyData, "addressStreet", "cbc:StreetName",
                    sb);
            appendIfPresent(partyData, "addressCityName",
                    "cbc:CityName", sb);
        }
        String postalCode = isSeller ? header.getSellerPostalCode()
                : header.getBuyerPostalCode();
        if (postalCode != null) {
            sb.append("<cbc:PostalZone>").append(esc(postalCode))
                    .append("</cbc:PostalZone>");
        } else if (partyData != null) {
            appendIfPresent(partyData, "addressPostalZone",
                    "cbc:PostalZone", sb);
        }
        String countryCode = isSeller ? header.getSellerCountryCode()
                : header.getBuyerCountryCode();
        if (countryCode != null) {
            sb.append("<cac:Country>");
            sb.append("<cbc:IdentificationCode>")
                    .append(esc(countryCode))
                    .append("</cbc:IdentificationCode>");
            sb.append("</cac:Country>");
        } else if (partyData != null) {
            Object country = partyData.get("addressCountryCode");
            if (country != null) {
                sb.append("<cac:Country>");
                sb.append("<cbc:IdentificationCode>")
                        .append(esc(country.toString()))
                        .append("</cbc:IdentificationCode>");
                sb.append("</cac:Country>");
            }
        }
        sb.append("</cac:PostalAddress>");
        sb.append("</cac:Party>");
        sb.append("</cac:").append(wrapper).append(">");
    }

    private void appendSimplifiedPartyFromPromoted(String wrapper,
            ZatcaSimplifiedHeader header, boolean isSeller,
            StringBuilder sb) {
        sb.append("<cac:").append(wrapper).append(">");
        sb.append("<cac:Party>");
        String vatNumber = isSeller ? header.getSellerVatNumber()
                : header.getBuyerVatNumber();
        if (vatNumber != null) {
            sb.append("<cac:PartyIdentification>");
            sb.append("<cbc:ID>").append(esc(vatNumber))
                    .append("</cbc:ID>");
            sb.append("</cac:PartyIdentification>");
        }
        sb.append("<cac:PartyName>");
        Map<String, Object> partyData = isSeller
                ? header.getSellerData() : header.getBuyerData();
        Object name = partyData != null
                ? partyData.getOrDefault("partyName",
                        partyData.get("nameEn")) : null;
        sb.append("<cbc:Name>")
                .append(esc(name != null ? name.toString() : ""))
                .append("</cbc:Name>");
        sb.append("</cac:PartyName>");
        sb.append("<cac:PostalAddress>");
        if (partyData != null) {
            appendIfPresent(partyData, "addressStreet", "cbc:StreetName",
                    sb);
            appendIfPresent(partyData, "addressCityName",
                    "cbc:CityName", sb);
        }
        String postalCode = isSeller ? header.getSellerPostalCode()
                : header.getBuyerPostalCode();
        if (postalCode != null) {
            sb.append("<cbc:PostalZone>").append(esc(postalCode))
                    .append("</cbc:PostalZone>");
        } else if (partyData != null) {
            appendIfPresent(partyData, "addressPostalZone",
                    "cbc:PostalZone", sb);
        }
        String countryCode = isSeller ? header.getSellerCountryCode()
                : header.getBuyerCountryCode();
        if (countryCode != null) {
            sb.append("<cac:Country>");
            sb.append("<cbc:IdentificationCode>")
                    .append(esc(countryCode))
                    .append("</cbc:IdentificationCode>");
            sb.append("</cac:Country>");
        } else if (partyData != null) {
            Object country = partyData.get("addressCountryCode");
            if (country != null) {
                sb.append("<cac:Country>");
                sb.append("<cbc:IdentificationCode>")
                        .append(esc(country.toString()))
                        .append("</cbc:IdentificationCode>");
                sb.append("</cac:Country>");
            }
        }
        sb.append("</cac:PostalAddress>");
        sb.append("</cac:Party>");
        sb.append("</cac:").append(wrapper).append(">");
    }

    private void appendAmountWithCurrency(String tag, BigDecimal value,
            String currencyId, StringBuilder sb) {
        sb.append("<").append(tag).append(" currencyID=\"")
                .append(esc(currencyId)).append("\">");
        if (value != null) {
            sb.append(value.setScale(2, java.math.RoundingMode.HALF_EVEN)
                    .toPlainString());
        } else {
            sb.append("0.00");
        }
        sb.append("</").append(tag).append(">");
    }

    private void appendParty(String wrapper,
            Map<String, Object> partyData, StringBuilder sb) {
        sb.append("<cac:").append(wrapper).append(">");
        sb.append("<cac:Party>");
        Object taxId = partyData.get("taxRegistrationNumber");
        if (taxId != null) {
            sb.append("<cac:PartyIdentification>");
            sb.append("<cbc:ID>")
                    .append(esc(taxId.toString()))
                    .append("</cbc:ID>");
            sb.append("</cac:PartyIdentification>");
        }
        sb.append("<cac:PartyName>");
        Object name = partyData.getOrDefault("partyName",
                partyData.get("nameEn"));
        sb.append("<cbc:Name>")
                .append(esc(name != null ? name.toString() : ""))
                .append("</cbc:Name>");
        sb.append("</cac:PartyName>");
        sb.append("<cac:PostalAddress>");
        appendIfPresent(partyData, "addressStreet", "cbc:StreetName",
                sb);
        appendIfPresent(partyData, "addressCityName",
                "cbc:CityName", sb);
        appendIfPresent(partyData, "addressPostalZone",
                "cbc:PostalZone", sb);
        Object country = partyData.get("addressCountryCode");
        if (country != null) {
            sb.append("<cac:Country>");
            sb.append("<cbc:IdentificationCode>")
                    .append(esc(country.toString()))
                    .append("</cbc:IdentificationCode>");
            sb.append("</cac:Country>");
        }
        sb.append("</cac:PostalAddress>");
        sb.append("</cac:Party>");
        sb.append("</cac:").append(wrapper).append(">");
    }

    private void appendIfPresent(Map<String, Object> data, String key,
            String tag, StringBuilder sb) {
        Object val = data.get(key);
        if (val != null) {
            sb.append("<").append(tag).append(">")
                    .append(esc(val.toString()))
                    .append("</").append(tag).append(">");
        }
    }

    private void appendLine(ZatcaStandardLine line, String currency,
            StringBuilder sb) {
        sb.append("<cac:InvoiceLine>");
        sb.append("<cbc:ID>").append(line.getLineNumber())
                .append("</cbc:ID>");
        sb.append("<cbc:InvoicedQuantity>")
                .append(line.getQuantity() != null
                        ? line.getQuantity().toPlainString() : "0")
                .append("</cbc:InvoicedQuantity>");
        appendAmount("cbc:LineExtensionAmount",
                line.getLineExtensionAmount(), currency, sb);
        sb.append("<cac:Item>");
        sb.append("<cbc:Description>")
                .append(esc(line.getDescription()))
                .append("</cbc:Description>");
        if (line.getItemCode() != null) {
            sb.append("<cac:StandardItemIdentification>");
            sb.append("<cbc:ID>").append(esc(line.getItemCode()))
                    .append("</cbc:ID>");
            sb.append("</cac:StandardItemIdentification>");
        }
        sb.append("</cac:Item>");
        sb.append("<cac:Price>");
        appendAmount("cbc:PriceAmount", line.getItemNetPrice(), currency,
                sb);
        sb.append("<cbc:BaseQuantity");
        if (line.getUnitType() != null && !line.getUnitType().isBlank()) {
            sb.append(" unitCode=\"").append(esc(line.getUnitType()))
                    .append("\"");
        }
        sb.append(">");
        sb.append(line.getItemPriceBaseQuantity() != null
                ? line.getItemPriceBaseQuantity().toPlainString() : "1");
        sb.append("</cbc:BaseQuantity>");
        if (line.getItemPriceDiscount() != null
                && line.getItemPriceDiscount()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:AllowanceCharge>");
            sb.append("<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
            appendAmount("cbc:Amount",
                    line.getItemPriceDiscount(), currency, sb);
            appendAmount("cbc:BaseAmount",
                    line.getItemGrossPrice() != null
                            ? line.getItemGrossPrice()
                            : line.getItemNetPrice(),
                    currency, sb);
            sb.append("</cac:AllowanceCharge>");
        }
        sb.append("</cac:Price>");

        if (line.getAllowances() != null
                && !line.getAllowances().isEmpty()) {
            for (var allowance : line.getAllowances()) {
                sb.append("<cac:AllowanceCharge>");
                sb.append(
                        "<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
                if (allowance.getReason() != null) {
                    sb.append("<cbc:AllowanceChargeReason>")
                            .append(esc(allowance.getReason()))
                            .append("</cbc:AllowanceChargeReason>");
                }
                if (allowance.getPercentage() != null) {
                    sb.append("<cbc:MultiplierFactorNumeric>")
                            .append(allowance.getPercentage().toPlainString())
                            .append("</cbc:MultiplierFactorNumeric>");
                }
                appendAmount("cbc:Amount", allowance.getAmount(), currency,
                        sb);
                if (allowance.getBaseAmount() != null) {
                    appendAmount("cbc:BaseAmount",
                            allowance.getBaseAmount(), currency, sb);
                }
                sb.append("</cac:AllowanceCharge>");
            }
        }

        if (line.getVatAmount() != null
                && line.getVatAmount()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:TaxTotal>");
            appendAmount("cbc:TaxAmount", line.getVatAmount(), currency,
                    sb);
            if (line.getVatInclusiveAmount() != null) {
                appendAmount("cbc:RoundingAmount",
                        line.getVatInclusiveAmount(), currency, sb);
            }
            sb.append("</cac:TaxTotal>");
        }

        sb.append("</cac:InvoiceLine>");
    }

    private void appendSimplifiedLine(ZatcaSimplifiedLine line,
            String currency, StringBuilder sb) {
        sb.append("<cac:InvoiceLine>");
        sb.append("<cbc:ID>").append(line.getLineNumber())
                .append("</cbc:ID>");
        sb.append("<cbc:InvoicedQuantity>")
                .append(line.getQuantity() != null
                        ? line.getQuantity().toPlainString() : "0")
                .append("</cbc:InvoicedQuantity>");
        appendAmount("cbc:LineExtensionAmount",
                line.getLineExtensionAmount(), currency, sb);
        sb.append("<cac:Item>");
        sb.append("<cbc:Description>")
                .append(esc(line.getDescription()))
                .append("</cbc:Description>");
        if (line.getItemCode() != null) {
            sb.append("<cac:StandardItemIdentification>");
            sb.append("<cbc:ID>").append(esc(line.getItemCode()))
                    .append("</cbc:ID>");
            sb.append("</cac:StandardItemIdentification>");
        }
        sb.append("</cac:Item>");
        sb.append("<cac:Price>");
        appendAmount("cbc:PriceAmount", line.getItemNetPrice(), currency,
                sb);
        sb.append("<cbc:BaseQuantity");
        if (line.getUnitType() != null && !line.getUnitType().isBlank()) {
            sb.append(" unitCode=\"").append(esc(line.getUnitType()))
                    .append("\"");
        }
        sb.append(">");
        sb.append(line.getItemPriceBaseQuantity() != null
                ? line.getItemPriceBaseQuantity().toPlainString() : "1");
        sb.append("</cbc:BaseQuantity>");
        if (line.getItemPriceDiscount() != null
                && line.getItemPriceDiscount()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:AllowanceCharge>");
            sb.append("<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
            appendAmount("cbc:Amount",
                    line.getItemPriceDiscount(), currency, sb);
            appendAmount("cbc:BaseAmount",
                    line.getItemGrossPrice() != null
                            ? line.getItemGrossPrice()
                            : line.getItemNetPrice(),
                    currency, sb);
            sb.append("</cac:AllowanceCharge>");
        }
        sb.append("</cac:Price>");

        if (line.getAllowances() != null
                && !line.getAllowances().isEmpty()) {
            for (var allowance : line.getAllowances()) {
                sb.append("<cac:AllowanceCharge>");
                sb.append(
                        "<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
                if (allowance.getReason() != null) {
                    sb.append("<cbc:AllowanceChargeReason>")
                            .append(esc(allowance.getReason()))
                            .append("</cbc:AllowanceChargeReason>");
                }
                if (allowance.getPercentage() != null) {
                    sb.append("<cbc:MultiplierFactorNumeric>")
                            .append(allowance.getPercentage().toPlainString())
                            .append("</cbc:MultiplierFactorNumeric>");
                }
                appendAmount("cbc:Amount", allowance.getAmount(), currency,
                        sb);
                if (allowance.getBaseAmount() != null) {
                    appendAmount("cbc:BaseAmount",
                            allowance.getBaseAmount(), currency, sb);
                }
                sb.append("</cac:AllowanceCharge>");
            }
        }

        if (line.getVatAmount() != null
                && line.getVatAmount()
                        .compareTo(BigDecimal.ZERO) > 0) {
            sb.append("<cac:TaxTotal>");
            appendAmount("cbc:TaxAmount", line.getVatAmount(), currency,
                    sb);
            if (line.getVatInclusiveAmount() != null) {
                appendAmount("cbc:RoundingAmount",
                        line.getVatInclusiveAmount(), currency, sb);
            }
            sb.append("</cac:TaxTotal>");
        }

        sb.append("</cac:InvoiceLine>");
    }

    private void appendAmount(String tag, BigDecimal value,
            String currency, StringBuilder sb) {
        sb.append("<").append(tag).append(">");
        if (value != null) {
            sb.append(value.setScale(2, java.math.RoundingMode.HALF_EVEN)
                    .toPlainString());
        } else {
            sb.append("0.00");
        }
        sb.append("</").append(tag).append(">");
    }

    private String esc(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void appendDocumentAllowance(BigDecimal amount,
            BigDecimal baseAmount, BigDecimal percentage, String reason,
            String reasonCode, String vatCategoryCode, BigDecimal vatRate,
            String currency, StringBuilder sb) {
        sb.append("<cac:AllowanceCharge>");
        sb.append("<cbc:ChargeIndicator>false</cbc:ChargeIndicator>");
        if (reasonCode != null) {
            sb.append("<cbc:AllowanceChargeReasonCode>")
                    .append(esc(reasonCode))
                    .append("</cbc:AllowanceChargeReasonCode>");
        }
        if (reason != null) {
            sb.append("<cbc:AllowanceChargeReason>")
                    .append(esc(reason))
                    .append("</cbc:AllowanceChargeReason>");
        }
        if (percentage != null) {
            sb.append("<cbc:MultiplierFactorNumeric>")
                    .append(percentage.toPlainString())
                    .append("</cbc:MultiplierFactorNumeric>");
        }
        appendAmount("cbc:Amount", amount, currency, sb);
        if (baseAmount != null) {
            appendAmount("cbc:BaseAmount", baseAmount, currency, sb);
        }
        if (vatCategoryCode != null) {
            sb.append("<cac:TaxCategory>");
            sb.append("<cbc:ID>").append(esc(vatCategoryCode))
                    .append("</cbc:ID>");
            if (vatRate != null) {
                sb.append("<cbc:Percent>")
                        .append(vatRate.toPlainString())
                        .append("</cbc:Percent>");
            }
            sb.append("<cac:TaxScheme>");
            sb.append("<cbc:ID>VAT</cbc:ID>");
            sb.append("</cac:TaxScheme>");
            sb.append("</cac:TaxCategory>");
        }
        sb.append("</cac:AllowanceCharge>");
    }

    private void appendTaxSubtotal(BigDecimal taxableAmount,
            BigDecimal taxAmount, String vatCategoryCode, BigDecimal vatRate,
            String exemptionReasonCode, String exemptionReasonText,
            String currency, StringBuilder sb) {
        sb.append("<cac:TaxSubtotal>");
        appendAmount("cbc:TaxableAmount", taxableAmount, currency, sb);
        appendAmount("cbc:TaxAmount", taxAmount, currency, sb);
        sb.append("<cac:TaxCategory>");
        if (vatCategoryCode != null) {
            sb.append("<cbc:ID>").append(esc(vatCategoryCode))
                    .append("</cbc:ID>");
        }
        if (vatRate != null) {
            sb.append("<cbc:Percent>")
                    .append(vatRate.toPlainString())
                    .append("</cbc:Percent>");
        }
        if (exemptionReasonCode != null) {
            sb.append("<cbc:TaxExemptionReasonCode>")
                    .append(esc(exemptionReasonCode))
                    .append("</cbc:TaxExemptionReasonCode>");
        }
        if (exemptionReasonText != null) {
            sb.append("<cbc:TaxExemptionReason>")
                    .append(esc(exemptionReasonText))
                    .append("</cbc:TaxExemptionReason>");
        }
        sb.append("<cac:TaxScheme>");
        sb.append("<cbc:ID>VAT</cbc:ID>");
        sb.append("</cac:TaxScheme>");
        sb.append("</cac:TaxCategory>");
        sb.append("</cac:TaxSubtotal>");
    }

    private Map<String, ZatcaStandardTaxSubtotal> dedupStandardSubtotals(
            List<ZatcaStandardTaxSubtotal> rows) {
        Map<String, ZatcaStandardTaxSubtotal> out = new LinkedHashMap<>();
        if (rows == null) {
            return out;
        }
        for (ZatcaStandardTaxSubtotal s : rows) {
            String key = subtotalKey(s.getVatCategoryCode(), s.getVatRate());
            out.putIfAbsent(key, s);
        }
        return out;
    }

    private Map<String, ZatcaSimplifiedTaxSubtotal> dedupSimplifiedSubtotals(
            List<ZatcaSimplifiedTaxSubtotal> rows) {
        Map<String, ZatcaSimplifiedTaxSubtotal> out = new LinkedHashMap<>();
        if (rows == null) {
            return out;
        }
        for (ZatcaSimplifiedTaxSubtotal s : rows) {
            String key = subtotalKey(s.getVatCategoryCode(), s.getVatRate());
            out.putIfAbsent(key, s);
        }
        return out;
    }

    private String subtotalKey(String vatCategoryCode, BigDecimal vatRate) {
        return Objects.toString(vatCategoryCode, "") + "|"
                + (vatRate == null ? "" : vatRate.stripTrailingZeros()
                        .toPlainString());
    }
}
