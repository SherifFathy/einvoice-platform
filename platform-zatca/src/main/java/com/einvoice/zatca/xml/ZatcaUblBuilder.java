package com.einvoice.zatca.xml;

import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import com.einvoice.core.domain.enums.InvoiceType;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * UBL 2.1 XML builder for ZATCA-compliant invoice documents.
 */
@Service
public class ZatcaUblBuilder {

    private static final String UBL_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String EXT_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String buildXml(Invoice invoice) {
        return buildXml(invoice, 1L, null);
    }

    public String buildXml(Invoice invoice, long invoiceCounter) {
        return buildXml(invoice, invoiceCounter, null);
    }

    /**
     * Builds a ZATCA-compliant UBL XML document for the given invoice.
     *
     * @param invoice the invoice to serialize
     * @param invoiceCounter the invoice counter value (ICV)
     * @param previousInvoiceHash the previous invoice hash (PIH)
     * @return the UBL XML string
     */
    public String buildXml(Invoice invoice, long invoiceCounter, String previousInvoiceHash) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElementNS(UBL_NS, "Invoice");
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:cbc", CBC_NS);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:cac", CAC_NS);
            root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:ext", EXT_NS);
            doc.appendChild(root);

            appendProfileId(doc, root, invoice);
            appendId(doc, root, invoice);
            appendUuid(doc, root, invoice);
            appendIssueDate(doc, root, invoice);
            appendIssueTime(doc, root, invoice);
            appendInvoiceTypeCode(doc, root, invoice);
            appendDocumentCurrencyCode(doc, root, invoice);
            appendTaxCurrencyCode(doc, root, invoice);
            appendBillingReference(doc, root, invoice);
            appendAdditionalDocumentReference(doc, root, invoiceCounter, previousInvoiceHash);
            appendAccountingSupplierParty(doc, root, invoice);
            appendAccountingCustomerParty(doc, root, invoice);
            appendDelivery(doc, root, invoice);
            appendPaymentMeans(doc, root, invoice);
            appendAllowanceCharge(doc, root, invoice);
            appendTaxTotal(doc, root, invoice);
            appendLegalMonetaryTotal(doc, root, invoice);
            appendInvoiceLines(doc, root, invoice);

            return serialize(doc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build UBL XML", e);
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendProfileId(Document doc, Element root, Invoice invoice) {
        Element profileId = doc.createElementNS(CBC_NS, "cbc:ProfileID");
        if (isReportingFlow(invoice)) {
            profileId.setTextContent("reporting:1.0");
        } else {
            profileId.setTextContent("clearance:1.0");
        }
        root.appendChild(profileId);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendId(Document doc, Element root, Invoice invoice) {
        Element id = doc.createElementNS(CBC_NS, "cbc:ID");
        id.setTextContent(invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "");
        root.appendChild(id);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendUuid(Document doc, Element root, Invoice invoice) {
        Element uuid = doc.createElementNS(CBC_NS, "cbc:UUID");
        uuid.setTextContent(invoice.getId().toString());
        root.appendChild(uuid);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendIssueDate(Document doc, Element root, Invoice invoice) {
        Element issueDate = doc.createElementNS(CBC_NS, "cbc:IssueDate");
        issueDate.setTextContent(resolveIssueDateTime(invoice)
                .atZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(DATE_FMT));
        root.appendChild(issueDate);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendIssueTime(Document doc, Element root, Invoice invoice) {
        Element issueTime = doc.createElementNS(CBC_NS, "cbc:IssueTime");
        issueTime.setTextContent(resolveIssueDateTime(invoice)
                .atZoneSameInstant(java.time.ZoneOffset.UTC)
                .format(TIME_FMT));
        root.appendChild(issueTime);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private OffsetDateTime resolveIssueDateTime(Invoice invoice) {
        if (invoice.getCreatedAt() != null) {
            return invoice.getCreatedAt();
        }
        if (invoice.getIssueDate() != null) {
            return invoice.getIssueDate().atStartOfDay()
                    .atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
        }
        return OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendInvoiceTypeCode(Document doc, Element root, Invoice invoice) {
        Element typeCode = doc.createElementNS(CBC_NS, "cbc:InvoiceTypeCode");
        typeCode.setAttribute("name", resolveTypeName(invoice));
        typeCode.setTextContent(resolveTypeCode(invoice.getType()));
        root.appendChild(typeCode);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendDocumentCurrencyCode(Document doc, Element root, Invoice invoice) {
        Element currencyCode = doc.createElementNS(CBC_NS, "cbc:DocumentCurrencyCode");
        currencyCode.setTextContent(invoice.getCurrency());
        root.appendChild(currencyCode);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendTaxCurrencyCode(Document doc, Element root, Invoice invoice) {
        Element taxCurrencyCode = doc.createElementNS(CBC_NS, "cbc:TaxCurrencyCode");
        taxCurrencyCode.setTextContent(invoice.getCurrency());
        root.appendChild(taxCurrencyCode);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendBillingReference(Document doc, Element root, Invoice invoice) {
        if (invoice.getOriginalInvoice() == null && invoice.getExternalInvoiceReference() == null) {
            return;
        }
        Element billingRef = doc.createElementNS(CAC_NS, "cac:BillingReference");
        Element invDocRef = doc.createElementNS(CAC_NS, "cac:InvoiceDocumentReference");
        if (invoice.getOriginalInvoice() != null && invoice.getOriginalInvoice().getInvoiceNumber() != null) {
            Element id = doc.createElementNS(CBC_NS, "cbc:ID");
            id.setTextContent(invoice.getOriginalInvoice().getInvoiceNumber());
            invDocRef.appendChild(id);
        } else if (invoice.getExternalInvoiceReference() != null) {
            Element id = doc.createElementNS(CBC_NS, "cbc:ID");
            id.setTextContent(invoice.getExternalInvoiceReference());
            invDocRef.appendChild(id);
        }
        billingRef.appendChild(invDocRef);
        root.appendChild(billingRef);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendAdditionalDocumentReference(Document doc, Element root,
            long invoiceCounter, String previousInvoiceHash) {
        Element icvRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
        Element icvId = doc.createElementNS(CBC_NS, "cbc:ID");
        icvId.setTextContent("ICV");
        icvRef.appendChild(icvId);
        Element icvUuid = doc.createElementNS(CBC_NS, "cbc:UUID");
        icvUuid.setTextContent(String.valueOf(invoiceCounter));
        icvRef.appendChild(icvUuid);
        root.appendChild(icvRef);

        Element pihRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
        Element pihId = doc.createElementNS(CBC_NS, "cbc:ID");
        pihId.setTextContent("PIH");
        pihRef.appendChild(pihId);
        Element pihDocRef = doc.createElementNS(CAC_NS, "cac:DocumentReference");
        Element pihDigest = doc.createElementNS(CBC_NS, "cbc:DigestValue");
        pihDigest.setTextContent(previousInvoiceHash != null && !previousInvoiceHash.isBlank()
                ? previousInvoiceHash
                : "");
        pihDocRef.appendChild(pihDigest);
        pihRef.appendChild(pihDocRef);
        root.appendChild(pihRef);

        Element qrRef = doc.createElementNS(CAC_NS, "cac:AdditionalDocumentReference");
        Element qrId = doc.createElementNS(CBC_NS, "cbc:ID");
        qrId.setTextContent("QR");
        qrRef.appendChild(qrId);
        Element qrAttachment = doc.createElementNS(CAC_NS, "cac:Attachment");
        Element qrBinary = doc.createElementNS(CBC_NS, "cbc:EmbeddedDocumentBinaryObject");
        qrBinary.setAttribute("mimeCode", "text/plain");
        qrBinary.setTextContent("");
        qrAttachment.appendChild(qrBinary);
        qrRef.appendChild(qrAttachment);
        root.appendChild(qrRef);
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    private void appendAccountingSupplierParty(Document doc, Element root, Invoice invoice) {
        Element supplierParty = doc.createElementNS(CAC_NS, "cac:AccountingSupplierParty");
        Element party = doc.createElementNS(CAC_NS, "cac:Party");

        Company company = invoice.getCompany();

        Element partyId = doc.createElementNS(CAC_NS, "cac:PartyIdentification");
        Element id = doc.createElementNS(CBC_NS, "cbc:ID");
        id.setAttribute("schemeID", "CRN");
        id.setTextContent(company.getCrNumber() != null ? company.getCrNumber() : "");
        partyId.appendChild(id);
        party.appendChild(partyId);

        Element partyName = doc.createElementNS(CAC_NS, "cac:PartyName");
        Element name = doc.createElementNS(CBC_NS, "cbc:Name");
        name.setTextContent(company.getNameEn());
        partyName.appendChild(name);
        party.appendChild(partyName);

        Element postalAddress = doc.createElementNS(CAC_NS, "cac:PostalAddress");
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:StreetName", company.getStreet());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:BuildingNumber", company.getBuildingNumber());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:CitySubdivisionName", company.getDistrict());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:CityName", company.getCity());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:PostalZone", company.getPostalCode());
        Element country = doc.createElementNS(CAC_NS, "cac:Country");
        Element countryCode = doc.createElementNS(CBC_NS, "cbc:IdentificationCode");
        countryCode.setTextContent(company.getCountryCode() != null ? company.getCountryCode() : "SA");
        country.appendChild(countryCode);
        postalAddress.appendChild(country);
        party.appendChild(postalAddress);

        Element partyTaxScheme = doc.createElementNS(CAC_NS, "cac:PartyTaxScheme");
        Element companyId = doc.createElementNS(CBC_NS, "cbc:CompanyID");
        companyId.setTextContent(company.getVatNumber());
        partyTaxScheme.appendChild(companyId);
        Element taxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
        Element taxSchemeId = doc.createElementNS(CBC_NS, "cbc:ID");
        taxSchemeId.setTextContent("VAT");
        taxScheme.appendChild(taxSchemeId);
        partyTaxScheme.appendChild(taxScheme);
        party.appendChild(partyTaxScheme);

        Element partyLegalEntity = doc.createElementNS(CAC_NS, "cac:PartyLegalEntity");
        Element regName = doc.createElementNS(CBC_NS, "cbc:RegistrationName");
        regName.setTextContent(company.getNameEn());
        partyLegalEntity.appendChild(regName);
        party.appendChild(partyLegalEntity);

        supplierParty.appendChild(party);
        root.appendChild(supplierParty);
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    private void appendAccountingCustomerParty(Document doc, Element root, Invoice invoice) {
        Customer buyer = invoice.getBuyer();
        if (buyer == null) {
            return;
        }
        Element customerParty = doc.createElementNS(CAC_NS, "cac:AccountingCustomerParty");
        Element party = doc.createElementNS(CAC_NS, "cac:Party");

        if (buyer.getIdType() != null && buyer.getIdValue() != null) {
            Element partyId = doc.createElementNS(CAC_NS, "cac:PartyIdentification");
            Element id = doc.createElementNS(CBC_NS, "cbc:ID");
            id.setAttribute("schemeID", buyer.getIdType());
            id.setTextContent(buyer.getIdValue());
            partyId.appendChild(id);
            party.appendChild(partyId);
        }

        Element partyName = doc.createElementNS(CAC_NS, "cac:PartyName");
        Element name = doc.createElementNS(CBC_NS, "cbc:Name");
        name.setTextContent(buyer.getNameEn());
        partyName.appendChild(name);
        party.appendChild(partyName);

        Element postalAddress = doc.createElementNS(CAC_NS, "cac:PostalAddress");
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:StreetName", buyer.getStreet());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:BuildingNumber", buyer.getBuildingNumber());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:CitySubdivisionName", buyer.getDistrict());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:CityName", buyer.getCity());
        appendTextElement(doc, postalAddress, CBC_NS, "cbc:PostalZone", buyer.getPostalCode());
        Element country = doc.createElementNS(CAC_NS, "cac:Country");
        Element countryCode = doc.createElementNS(CBC_NS, "cbc:IdentificationCode");
        countryCode.setTextContent(buyer.getCountryCode() != null ? buyer.getCountryCode() : "SA");
        country.appendChild(countryCode);
        postalAddress.appendChild(country);
        party.appendChild(postalAddress);

        if (buyer.getVatNumber() != null) {
            Element partyTaxScheme = doc.createElementNS(CAC_NS, "cac:PartyTaxScheme");
            Element companyId = doc.createElementNS(CBC_NS, "cbc:CompanyID");
            companyId.setTextContent(buyer.getVatNumber());
            partyTaxScheme.appendChild(companyId);
            Element taxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
            Element taxSchemeId = doc.createElementNS(CBC_NS, "cbc:ID");
            taxSchemeId.setTextContent("VAT");
            taxScheme.appendChild(taxSchemeId);
            partyTaxScheme.appendChild(taxScheme);
            party.appendChild(partyTaxScheme);
        }

        Element partyLegalEntity = doc.createElementNS(CAC_NS, "cac:PartyLegalEntity");
        Element regName = doc.createElementNS(CBC_NS, "cbc:RegistrationName");
        regName.setTextContent(buyer.getNameEn());
        partyLegalEntity.appendChild(regName);
        party.appendChild(partyLegalEntity);

        customerParty.appendChild(party);
        root.appendChild(customerParty);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendDelivery(Document doc, Element root, Invoice invoice) {
        if (invoice.getSupplyDate() == null) {
            return;
        }
        Element delivery = doc.createElementNS(CAC_NS, "cac:Delivery");
        Element actualDate = doc.createElementNS(CBC_NS, "cbc:ActualDeliveryDate");
        actualDate.setTextContent(invoice.getSupplyDate().format(DATE_FMT));
        delivery.appendChild(actualDate);
        root.appendChild(delivery);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendPaymentMeans(Document doc, Element root, Invoice invoice) {
        Element paymentMeans = doc.createElementNS(CAC_NS, "cac:PaymentMeans");
        Element meansCode = doc.createElementNS(CBC_NS, "cbc:PaymentMeansCode");
        meansCode.setTextContent(invoice.getPaymentMeansCode() != null ? invoice.getPaymentMeansCode() : "10");
        paymentMeans.appendChild(meansCode);
        root.appendChild(paymentMeans);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendAllowanceCharge(Document doc, Element root, Invoice invoice) {
        if (invoice.getTotalAllowances() == null
                || invoice.getTotalAllowances().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        Element charge = doc.createElementNS(CAC_NS, "cac:AllowanceCharge");
        Element chargeIndicator = doc.createElementNS(CBC_NS, "cbc:ChargeIndicator");
        chargeIndicator.setTextContent("false");
        charge.appendChild(chargeIndicator);
        Element amount = doc.createElementNS(CBC_NS, "cbc:Amount");
        amount.setAttribute("currencyID", invoice.getCurrency());
        amount.setTextContent(formatAmount(invoice.getTotalAllowances()));
        charge.appendChild(amount);
        Element taxCategory = doc.createElementNS(CAC_NS, "cac:TaxCategory");
        Element taxId = doc.createElementNS(CBC_NS, "cbc:ID");
        taxId.setTextContent("S");
        taxCategory.appendChild(taxId);
        Element taxPercent = doc.createElementNS(CBC_NS, "cbc:Percent");
        String vatRate = resolveFirstVatRate(invoice);
        taxPercent.setTextContent(vatRate);
        taxCategory.appendChild(taxPercent);
        Element taxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
        Element schemeId = doc.createElementNS(CBC_NS, "cbc:ID");
        schemeId.setTextContent("VAT");
        taxScheme.appendChild(schemeId);
        taxCategory.appendChild(taxScheme);
        charge.appendChild(taxCategory);
        root.appendChild(charge);
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    private void appendTaxTotal(Document doc, Element root, Invoice invoice) {
        List<InvoiceVatBreakdown> breakdowns = invoice.getVatBreakdown();

        Element taxTotal = doc.createElementNS(CAC_NS, "cac:TaxTotal");
        Element taxAmount = doc.createElementNS(CBC_NS, "cbc:TaxAmount");
        taxAmount.setAttribute("currencyID", invoice.getCurrency());
        taxAmount.setTextContent(formatAmount(invoice.getTotalVat()));
        taxTotal.appendChild(taxAmount);

        if (breakdowns != null) {
            for (InvoiceVatBreakdown bd : breakdowns) {
                Element taxSubtotal = doc.createElementNS(CAC_NS, "cac:TaxSubtotal");
                Element taxableAmount = doc.createElementNS(CBC_NS, "cbc:TaxableAmount");
                taxableAmount.setAttribute("currencyID", invoice.getCurrency());
                taxableAmount.setTextContent(formatAmount(bd.getTaxableAmount()));
                taxSubtotal.appendChild(taxableAmount);
                Element bdTaxAmount = doc.createElementNS(CBC_NS, "cbc:TaxAmount");
                bdTaxAmount.setAttribute("currencyID", invoice.getCurrency());
                bdTaxAmount.setTextContent(formatAmount(bd.getTaxAmount()));
                taxSubtotal.appendChild(bdTaxAmount);
                Element taxCategory = doc.createElementNS(CAC_NS, "cac:TaxCategory");
                Element catId = doc.createElementNS(CBC_NS, "cbc:ID");
                catId.setTextContent(bd.getVatCategoryCode());
                taxCategory.appendChild(catId);
                Element percent = doc.createElementNS(CBC_NS, "cbc:Percent");
                percent.setTextContent(formatAmount(bd.getVatRate()));
                taxCategory.appendChild(percent);
                Element taxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
                Element schemeId = doc.createElementNS(CBC_NS, "cbc:ID");
                schemeId.setTextContent("VAT");
                taxScheme.appendChild(schemeId);
                taxCategory.appendChild(taxScheme);
                taxSubtotal.appendChild(taxCategory);
                taxTotal.appendChild(taxSubtotal);
            }
        }
        root.appendChild(taxTotal);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendLegalMonetaryTotal(Document doc, Element root, Invoice invoice) {
        Element monetaryTotal = doc.createElementNS(CAC_NS, "cac:LegalMonetaryTotal");
        appendAmountElement(doc, monetaryTotal, CBC_NS, "cbc:LineExtensionAmount",
                invoice.getTotalLineNet(), invoice.getCurrency());
        appendAmountElement(doc, monetaryTotal, CBC_NS, "cbc:TaxExclusiveAmount",
                invoice.getTotalWithoutVat(), invoice.getCurrency());
        appendAmountElement(doc, monetaryTotal, CBC_NS, "cbc:TaxInclusiveAmount",
                invoice.getTotalWithVat(), invoice.getCurrency());
        if (invoice.getPrepaidAmount() != null
                && invoice.getPrepaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            appendAmountElement(doc, monetaryTotal, CBC_NS, "cbc:PrepaidAmount",
                    invoice.getPrepaidAmount(), invoice.getCurrency());
        }
        appendAmountElement(doc, monetaryTotal, CBC_NS, "cbc:AllowanceTotalAmount",
                invoice.getTotalAllowances(), invoice.getCurrency());
        appendAmountElement(doc, monetaryTotal, CBC_NS, "cbc:PayableAmount",
                invoice.getAmountDue(), invoice.getCurrency());
        root.appendChild(monetaryTotal);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendInvoiceLines(Document doc, Element root, Invoice invoice) {
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null) {
            return;
        }
        for (InvoiceLine line : lines) {
            Element lineElement = doc.createElementNS(CAC_NS, "cac:InvoiceLine");
            Element lineId = doc.createElementNS(CBC_NS, "cbc:ID");
            lineId.setTextContent(String.valueOf(line.getSortOrder()));
            lineElement.appendChild(lineId);
            if (line.getId() != null) {
                Element uuid = doc.createElementNS(CBC_NS, "cbc:UUID");
                uuid.setTextContent(String.valueOf(line.getId()));
                lineElement.appendChild(uuid);
            }
            Element qty = doc.createElementNS(CBC_NS, "cbc:InvoicedQuantity");
            qty.setAttribute("unitCode", line.getUnit() != null ? line.getUnit() : "EA");
            qty.setTextContent(formatAmount(line.getQuantity()));
            lineElement.appendChild(qty);
            Element lineExtAmount = doc.createElementNS(CBC_NS, "cbc:LineExtensionAmount");
            lineExtAmount.setAttribute("currencyID", invoice.getCurrency());
            lineExtAmount.setTextContent(formatAmount(line.getLineNetAmount()));
            lineElement.appendChild(lineExtAmount);

            if (line.getDiscountAmount() != null
                    && line.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                Element allowance = doc.createElementNS(CAC_NS, "cac:AllowanceCharge");
                Element indicator = doc.createElementNS(CBC_NS, "cbc:ChargeIndicator");
                indicator.setTextContent("false");
                allowance.appendChild(indicator);
                Element amount = doc.createElementNS(CBC_NS, "cbc:Amount");
                amount.setAttribute("currencyID", invoice.getCurrency());
                amount.setTextContent(formatAmount(line.getDiscountAmount()));
                allowance.appendChild(amount);
                lineElement.appendChild(allowance);
            }

            Element taxTotal = doc.createElementNS(CAC_NS, "cac:TaxTotal");
            Element taxAmount = doc.createElementNS(CBC_NS, "cbc:TaxAmount");
            taxAmount.setAttribute("currencyID", invoice.getCurrency());
            taxAmount.setTextContent(formatAmount(line.getLineVatAmount()));
            taxTotal.appendChild(taxAmount);
            Element taxSubtotal = doc.createElementNS(CAC_NS, "cac:TaxSubtotal");
            Element taxableAmount = doc.createElementNS(CBC_NS, "cbc:TaxableAmount");
            taxableAmount.setAttribute("currencyID", invoice.getCurrency());
            taxableAmount.setTextContent(formatAmount(line.getLineNetAmount()));
            taxSubtotal.appendChild(taxableAmount);
            Element bdTaxAmount = doc.createElementNS(CBC_NS, "cbc:TaxAmount");
            bdTaxAmount.setAttribute("currencyID", invoice.getCurrency());
            bdTaxAmount.setTextContent(formatAmount(line.getLineVatAmount()));
            taxSubtotal.appendChild(bdTaxAmount);
            Element taxCategory = doc.createElementNS(CAC_NS, "cac:TaxCategory");
            Element catId = doc.createElementNS(CBC_NS, "cbc:ID");
            catId.setTextContent(line.getVatCategory());
            taxCategory.appendChild(catId);
            Element percent = doc.createElementNS(CBC_NS, "cbc:Percent");
            percent.setTextContent(formatAmount(line.getVatRate()));
            taxCategory.appendChild(percent);
            Element taxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
            Element schemeId = doc.createElementNS(CBC_NS, "cbc:ID");
            schemeId.setTextContent("VAT");
            taxScheme.appendChild(schemeId);
            taxCategory.appendChild(taxScheme);
            taxSubtotal.appendChild(taxCategory);
            taxTotal.appendChild(taxSubtotal);
            lineElement.appendChild(taxTotal);

            Element item = doc.createElementNS(CAC_NS, "cac:Item");
            Element description = doc.createElementNS(CBC_NS, "cbc:Description");
            description.setTextContent(line.getDescriptionEn());
            item.appendChild(description);
            Element itemName = doc.createElementNS(CBC_NS, "cbc:Name");
            itemName.setTextContent(line.getDescriptionEn());
            item.appendChild(itemName);
            Element buyersItemIdentification = doc.createElementNS(CAC_NS, "cac:BuyersItemIdentification");
            if (line.getItem() != null) {
                Element itemId = doc.createElementNS(CBC_NS, "cbc:ID");
                itemId.setTextContent(line.getItem().getCode());
                buyersItemIdentification.appendChild(itemId);
            }
            item.appendChild(buyersItemIdentification);
            Element classifiedTaxCategory = doc.createElementNS(CAC_NS, "cac:ClassifiedTaxCategory");
            Element ctcId = doc.createElementNS(CBC_NS, "cbc:ID");
            ctcId.setTextContent(line.getVatCategory());
            classifiedTaxCategory.appendChild(ctcId);
            Element ctcPercent = doc.createElementNS(CBC_NS, "cbc:Percent");
            ctcPercent.setTextContent(formatAmount(line.getVatRate()));
            classifiedTaxCategory.appendChild(ctcPercent);
            Element ctcTaxScheme = doc.createElementNS(CAC_NS, "cac:TaxScheme");
            Element ctcSchemeId = doc.createElementNS(CBC_NS, "cbc:ID");
            ctcSchemeId.setTextContent("VAT");
            ctcTaxScheme.appendChild(ctcSchemeId);
            classifiedTaxCategory.appendChild(ctcTaxScheme);
            item.appendChild(classifiedTaxCategory);
            lineElement.appendChild(item);

            Element price = doc.createElementNS(CAC_NS, "cac:Price");
            Element priceAmount = doc.createElementNS(CBC_NS, "cbc:PriceAmount");
            priceAmount.setAttribute("currencyID", invoice.getCurrency());
            priceAmount.setTextContent(formatAmount(line.getUnitPrice()));
            price.appendChild(priceAmount);
            lineElement.appendChild(price);

            root.appendChild(lineElement);
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendTextElement(Document doc, Element parent, String ns,
            String localName, String textContent) {
        if (textContent == null) {
            return;
        }
        Element el = doc.createElementNS(ns, localName);
        el.setTextContent(textContent);
        parent.appendChild(el);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void appendAmountElement(Document doc, Element parent, String ns,
            String localName, BigDecimal amount, String currency) {
        if (amount == null) {
            return;
        }
        Element el = doc.createElementNS(ns, localName);
        el.setAttribute("currencyID", currency);
        el.setTextContent(formatAmount(amount));
        parent.appendChild(el);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveTypeCode(InvoiceType type) {
        return switch (type) {
          case TAX_INVOICE -> "388";
          case SIMPLIFIED_TAX_INVOICE -> "386";
          case CREDIT_NOTE -> "381";
          case DEBIT_NOTE -> "383";
        };
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveTypeName(Invoice invoice) {
        String base = switch (invoice.getType()) {
          case TAX_INVOICE -> "0100000";
          case SIMPLIFIED_TAX_INVOICE -> "0100000";
          case CREDIT_NOTE -> "0200000";
          case DEBIT_NOTE -> "0300000";
        };
        String flags = invoice.getSubtypeFlags();
        if (flags == null || flags.isBlank() || "{}".equals(flags.trim())) {
            return base;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(flags);
            if (node.path("thirdParty").asBoolean(false)) {
                base = replaceChar(base, 2, '1');
            }
            if (node.path("nominal").asBoolean(false)) {
                base = replaceChar(base, 3, '1');
            }
            if (node.path("exports").asBoolean(false)) {
                base = replaceChar(base, 4, '1');
            }
            if (node.path("summary").asBoolean(false)) {
                base = replaceChar(base, 5, '1');
            }
            if (node.path("selfBilled").asBoolean(false)) {
                base = replaceChar(base, 6, '1');
            }
        } catch (Exception ignored) {
        }
        return base;
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String replaceChar(String s, int pos, char c) {
        if (pos < 0 || pos >= s.length()) {
            return s;
        }
        return s.substring(0, pos) + c + s.substring(pos + 1);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private boolean isReportingFlow(Invoice invoice) {
        if (invoice.getType() == InvoiceType.SIMPLIFIED_TAX_INVOICE) {
            return true;
        }
        if (invoice.getType() == InvoiceType.CREDIT_NOTE
                || invoice.getType() == InvoiceType.DEBIT_NOTE) {
            return hasSimplifiedFlag(invoice.getSubtypeFlags());
        }
        return false;
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private boolean hasSimplifiedFlag(String subtypeFlags) {
        if (subtypeFlags == null || subtypeFlags.isBlank()) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(subtypeFlags);
            return node.path("simplified").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveFirstVatRate(Invoice invoice) {
        if (invoice.getVatBreakdown() != null && !invoice.getVatBreakdown().isEmpty()) {
            return formatAmount(invoice.getVatBreakdown().get(0).getVatRate());
        }
        if (invoice.getLines() != null && !invoice.getLines().isEmpty()) {
            return formatAmount(invoice.getLines().get(0).getVatRate());
        }
        return "15.00";
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String serialize(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
