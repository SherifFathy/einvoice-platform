package com.einvoice.api.integration.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.integration.dto.shared.DocumentIngestionResponse;
import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.api.integration.filter.IngestionRequestAttributes;
import com.einvoice.api.integration.dto.zatca.ZatcaSimplifiedInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.zatca.ZatcaStandardInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.zatca.ZatcaStandardInvoiceIngestionRequest.HeaderAllowance;
import com.einvoice.api.integration.dto.zatca.ZatcaStandardInvoiceIngestionRequest.LineItem;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedAllowance;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedLine;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedLineAllowance;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedTaxSubtotal;
import com.einvoice.core.domain.zatca.ZatcaStandardAllowance;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardLine;
import com.einvoice.core.domain.zatca.ZatcaStandardLineAllowance;
import com.einvoice.core.domain.zatca.ZatcaStandardTaxSubtotal;
import com.einvoice.core.error.DuplicateSimplifiedNumberException;
import com.einvoice.core.error.DuplicateStandardNumberException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.VatExemptionReasonRequiredException;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedAllowanceRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedHeaderRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedLineAllowanceRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedTaxSubtotalRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardAllowanceRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardHeaderRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardLineAllowanceRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardTaxSubtotalRepository;
import com.einvoice.security.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ZATCA ingestion service. Orchestrates company/environment resolution, dedup,
 * cross-field validation, header + lines + child-table persistence, and audit
 * logging for Standard (B2B) and Simplified (B2C) ZATCA invoice ingestion.
 */
@Service
@Transactional
public class ZatcaIngestionService {

    private final CompanyResolutionService companyResolutionService;
    private final ZatcaStandardHeaderRepository zatcaStandardHeaderRepository;
    private final ZatcaSimplifiedHeaderRepository zatcaSimplifiedHeaderRepository;
    private final ZatcaStandardTaxSubtotalRepository zatcaStandardTaxSubtotalRepository;
    private final ZatcaStandardAllowanceRepository zatcaStandardAllowanceRepository;
    private final ZatcaStandardLineAllowanceRepository zatcaStandardLineAllowanceRepository;
    private final ZatcaSimplifiedTaxSubtotalRepository zatcaSimplifiedTaxSubtotalRepository;
    private final ZatcaSimplifiedAllowanceRepository zatcaSimplifiedAllowanceRepository;
    private final ZatcaSimplifiedLineAllowanceRepository zatcaSimplifiedLineAllowanceRepository;
    private final AuditService auditService;
    private final ZatcaConfigRepository zatcaConfigRepository;
    private final UUID gatewayPrincipalId;

    /**
     * All-repository constructor.
     */
    public ZatcaIngestionService(
            CompanyResolutionService companyResolutionService,
            ZatcaStandardHeaderRepository zatcaStandardHeaderRepository,
            ZatcaSimplifiedHeaderRepository zatcaSimplifiedHeaderRepository,
            ZatcaStandardTaxSubtotalRepository zatcaStandardTaxSubtotalRepository,
            ZatcaStandardAllowanceRepository zatcaStandardAllowanceRepository,
            ZatcaStandardLineAllowanceRepository zatcaStandardLineAllowanceRepository,
            ZatcaSimplifiedTaxSubtotalRepository zatcaSimplifiedTaxSubtotalRepository,
            ZatcaSimplifiedAllowanceRepository zatcaSimplifiedAllowanceRepository,
            ZatcaSimplifiedLineAllowanceRepository zatcaSimplifiedLineAllowanceRepository,
            AuditService auditService,
            ZatcaConfigRepository zatcaConfigRepository,
            @Value("${einvoice.integration.gateway-principal-id}") UUID gatewayPrincipalId) {
        this.companyResolutionService = companyResolutionService;
        this.zatcaStandardHeaderRepository = zatcaStandardHeaderRepository;
        this.zatcaSimplifiedHeaderRepository = zatcaSimplifiedHeaderRepository;
        this.zatcaStandardTaxSubtotalRepository = zatcaStandardTaxSubtotalRepository;
        this.zatcaStandardAllowanceRepository = zatcaStandardAllowanceRepository;
        this.zatcaStandardLineAllowanceRepository = zatcaStandardLineAllowanceRepository;
        this.zatcaSimplifiedTaxSubtotalRepository = zatcaSimplifiedTaxSubtotalRepository;
        this.zatcaSimplifiedAllowanceRepository = zatcaSimplifiedAllowanceRepository;
        this.zatcaSimplifiedLineAllowanceRepository = zatcaSimplifiedLineAllowanceRepository;
        this.auditService = auditService;
        this.zatcaConfigRepository = zatcaConfigRepository;
        this.gatewayPrincipalId = gatewayPrincipalId;
    }

    /**
     * Ingests a ZATCA Standard (B2B) invoice.
     * Populates TenantContext for the duration of the call.
     */
    public DocumentIngestionResponse ingestStandard(ZatcaStandardInvoiceIngestionRequest req) {
        CompanyResolutionService.ResolvedContext ctx = companyResolutionService.resolve(
                req.companyRegistrationNumber(), "ZATCA", req.environment().name());

        populateTenantContext(ctx, req.environment().name());
        try {
            return doIngestStandard(req, ctx);
        } finally {
            TenantContext.clear();
        }
    }

    private DocumentIngestionResponse doIngestStandard(ZatcaStandardInvoiceIngestionRequest req,
            CompanyResolutionService.ResolvedContext ctx) {

        if (zatcaStandardHeaderRepository
                .existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
                        ctx.companyId(), ctx.authorityEnvironmentId(), req.invoiceNumber())) {
            throw new DuplicateStandardNumberException(
                    "Standard invoice '" + req.invoiceNumber()
                            + "' already exists for this company and environment.",
                    ctx.companyId(), req.invoiceNumber());
        }

        String typeCode = req.invoiceTypeCode();
        if (("381".equals(typeCode) || "383".equals(typeCode))
                && req.originalInvoiceNumber() == null) {
            throw new MissingOriginalDocumentException(
                    "Invoice type code " + typeCode + " requires originalInvoiceNumber",
                    null, typeCode);
        }

        for (LineItem li : req.lines()) {
            String cat = li.vatCategoryCode();
            if (("E".equals(cat) || "O".equals(cat))
                    && (li.exemptionReasonCode() == null || li.exemptionReasonCode().isBlank()
                    || li.exemptionReasonText() == null || li.exemptionReasonText().isBlank())) {
                throw new VatExemptionReasonRequiredException(
                        "VAT category " + cat + " requires exemption reason", cat);
            }
        }

        UUID originalInvoiceId = null;
        if (req.originalInvoiceNumber() != null) {
            originalInvoiceId = zatcaStandardHeaderRepository
                    .findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
                            ctx.companyId(), ctx.authorityEnvironmentId(),
                            req.originalInvoiceNumber())
                    .map(ZatcaStandardHeader::getId)
                    .orElse(null);
        }

        UUID zatcaConfigId = zatcaConfigRepository
                .findByCompanyAndAuthorityEnvironment(ctx.companyId(), ctx.authorityEnvironmentId())
                .map(com.einvoice.core.domain.config.ZatcaConfig::getId)
                .orElse(null);

        ZatcaStandardHeader header = ZatcaStandardHeader.builder()
                .companyId(ctx.companyId())
                .authorityEnvironmentId(ctx.authorityEnvironmentId())
                .invoiceNumber(req.invoiceNumber())
                .invoiceTypeCode(req.invoiceTypeCode())
                .transactionTypeCode(req.transactionTypeCode())
                .issueDate(req.issueDate())
                .issueTime(req.issueTime())
                .sellerData(toSellerPartyMap(req.seller()))
                .sellerPartyId(req.seller().partyId())
                .sellerPartyIdScheme(req.seller().partyIdScheme())
                .sellerVatNumber(req.seller().vatNumber())
                .sellerGroupVatNumber(req.seller().groupVatNumber())
                .sellerBuildingNumber(req.seller().buildingNumber())
                .sellerAdditionalNumber(req.seller().additionalNumber())
                .sellerPostalCode(req.seller().postalCode())
                .sellerCountryCode(req.seller().countryCode())
                .buyerData(toBuyerPartyMap(req.buyer()))
                .buyerPartyId(req.buyer().partyId())
                .buyerPartyIdScheme(req.buyer().partyIdScheme())
                .buyerVatNumber(req.buyer().vatNumber())
                .buyerGroupVatNumber(req.buyer().groupVatNumber())
                .buyerBuildingNumber(req.buyer().buildingNumber())
                .buyerAdditionalNumber(req.buyer().additionalNumber())
                .buyerPostalCode(req.buyer().postalCode())
                .buyerCountryCode(req.buyer().countryCode())
                .currency(req.currency())
                .lineExtensionAmount(req.lineExtensionAmount())
                .taxExclusiveAmount(req.taxExclusiveAmount())
                .taxAmount(req.taxAmount())
                .taxInclusiveAmount(req.taxInclusiveAmount())
                .prepaidAmount(req.prepaidAmount() != null ? req.prepaidAmount() : BigDecimal.ZERO)
                .payableAmount(req.payableAmount())
                .taxAmountAccountingCurrency(req.taxAmount())
                .paymentMeansCode(req.paymentMeansCode())
                .paymentMeansText(req.paymentMeansText())
                .invoiceCounterValue(req.invoiceCounterValue())
                .previousInvoiceHash(req.previousInvoiceHash())
                .invoiceHash(req.invoiceHash())
                .qrCodeBase64(req.qrCodeBase64())
                .clearanceStatus(req.clearanceStatus())
                .originalInvoiceNumber(req.originalInvoiceNumber())
                .originalInvoiceId(originalInvoiceId)
                .erpReferenceId(req.erpReferenceId())
                .zatcaConfigId(zatcaConfigId)
                .status(toDocumentState(req.status()))
                .createdBy(gatewayPrincipalId)
                .build();

        header.setLines(buildStandardLines(req, header));
        header.setTaxSubtotals(buildTaxSubtotals(req, header));
        header.setAllowances(buildHeaderAllowances(req, header));

        ZatcaStandardHeader saved = zatcaStandardHeaderRepository.save(header);
        IngestionRequestAttributes.stashDocument(saved.getId(),
                IngestionRequestAttributes.DOC_TYPE_ZATCA_STANDARD);

        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("invoiceNumber", req.invoiceNumber());
        if (req.erpReferenceId() != null) {
            auditPayload.put("erpReferenceId", req.erpReferenceId());
        }
        auditService.record("INGESTED", "ZATCA_STANDARD", saved.getId().toString(),
                null, auditPayload);

        return new DocumentIngestionResponse(
                saved.getId(),
                saved.getInvoiceNumber(),
                req.erpReferenceId(),
                saved.getStatus().name(),
                "Standard invoice ingested successfully");
    }

    public DocumentIngestionResponse ingestSimplified(ZatcaSimplifiedInvoiceIngestionRequest req) {
        CompanyResolutionService.ResolvedContext ctx = companyResolutionService.resolve(
                req.companyRegistrationNumber(), "ZATCA", req.environment().name());

        populateTenantContext(ctx, req.environment().name());
        try {
            return doIngestSimplified(req, ctx);
        } finally {
            TenantContext.clear();
        }
    }

    private DocumentIngestionResponse doIngestSimplified(ZatcaSimplifiedInvoiceIngestionRequest req,
            CompanyResolutionService.ResolvedContext ctx) {

        if (zatcaSimplifiedHeaderRepository
                .existsByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
                        ctx.companyId(), ctx.authorityEnvironmentId(), req.invoiceNumber())) {
            throw new DuplicateSimplifiedNumberException(
                    "Simplified invoice '" + req.invoiceNumber()
                            + "' already exists for this company and environment.",
                    ctx.companyId(), req.invoiceNumber());
        }

        String typeCode = req.invoiceTypeCode();
        if (("381".equals(typeCode) || "383".equals(typeCode))
                && req.originalInvoiceNumber() == null) {
            throw new MissingOriginalDocumentException(
                    "Invoice type code " + typeCode + " requires originalInvoiceNumber",
                    null, typeCode);
        }

        for (ZatcaSimplifiedInvoiceIngestionRequest.LineItem li : req.lines()) {
            String cat = li.vatCategoryCode();
            if (("E".equals(cat) || "O".equals(cat))
                    && (li.exemptionReasonCode() == null || li.exemptionReasonCode().isBlank()
                    || li.exemptionReasonText() == null || li.exemptionReasonText().isBlank())) {
                throw new VatExemptionReasonRequiredException(
                        "VAT category " + cat + " requires exemption reason", cat);
            }
        }

        UUID originalInvoiceId = null;
        if (req.originalInvoiceNumber() != null) {
            originalInvoiceId = zatcaSimplifiedHeaderRepository
                    .findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
                            ctx.companyId(), ctx.authorityEnvironmentId(),
                            req.originalInvoiceNumber())
                    .map(ZatcaSimplifiedHeader::getId)
                    .orElse(null);
        }

        UUID zatcaConfigId = zatcaConfigRepository
                .findByCompanyAndAuthorityEnvironment(ctx.companyId(), ctx.authorityEnvironmentId())
                .map(com.einvoice.core.domain.config.ZatcaConfig::getId)
                .orElse(null);

        ZatcaSimplifiedHeader.ZatcaSimplifiedHeaderBuilder headerBuilder = ZatcaSimplifiedHeader.builder()
                .companyId(ctx.companyId())
                .authorityEnvironmentId(ctx.authorityEnvironmentId())
                .invoiceNumber(req.invoiceNumber())
                .invoiceTypeCode(req.invoiceTypeCode())
                .transactionTypeCode(req.transactionTypeCode())
                .issueDate(req.issueDate())
                .issueTime(req.issueTime())
                .sellerData(toSimplifiedSellerPartyMap(req.seller()))
                .sellerPartyId(req.seller().partyId())
                .sellerPartyIdScheme(req.seller().partyIdScheme())
                .sellerVatNumber(req.seller().vatNumber())
                .sellerGroupVatNumber(req.seller().groupVatNumber())
                .sellerBuildingNumber(req.seller().buildingNumber())
                .sellerAdditionalNumber(req.seller().additionalNumber())
                .sellerPostalCode(req.seller().postalCode())
                .sellerCountryCode(req.seller().countryCode())
                .currency(req.currency())
                .lineExtensionAmount(req.lineExtensionAmount())
                .taxExclusiveAmount(req.taxExclusiveAmount())
                .taxAmount(req.taxAmount())
                .taxInclusiveAmount(req.taxInclusiveAmount())
                .prepaidAmount(req.prepaidAmount() != null ? req.prepaidAmount() : BigDecimal.ZERO)
                .payableAmount(req.payableAmount())
                .taxAmountAccountingCurrency(req.taxAmount())
                .paymentMeansCode(req.paymentMeansCode())
                .paymentMeansText(req.paymentMeansText())
                .invoiceCounterValue(req.invoiceCounterValue())
                .previousInvoiceHash(req.previousInvoiceHash())
                .invoiceHash(req.invoiceHash())
                .qrCodeBase64(req.qrCodeBase64())
                .reportingStatus(req.reportingStatus())
                .originalInvoiceNumber(req.originalInvoiceNumber())
                .originalInvoiceId(originalInvoiceId)
                .erpReferenceId(req.erpReferenceId())
                .zatcaConfigId(zatcaConfigId)
                .status(toDocumentState(req.status()))
                .createdBy(gatewayPrincipalId);

        if (req.buyer() != null) {
            headerBuilder
                    .buyerData(toSimplifiedBuyerPartyMap(req.buyer()))
                    .buyerPartyId(req.buyer().partyId())
                    .buyerPartyIdScheme(req.buyer().partyIdScheme())
                    .buyerVatNumber(req.buyer().vatNumber())
                    .buyerGroupVatNumber(req.buyer().groupVatNumber())
                    .buyerBuildingNumber(req.buyer().buildingNumber())
                    .buyerAdditionalNumber(req.buyer().additionalNumber())
                    .buyerPostalCode(req.buyer().postalCode())
                    .buyerCountryCode(req.buyer().countryCode());
        }

        ZatcaSimplifiedHeader header = headerBuilder.build();

        header.setLines(buildSimplifiedLines(req, header));
        header.setTaxSubtotals(buildSimplifiedTaxSubtotals(req, header));
        header.setAllowances(buildSimplifiedHeaderAllowances(req, header));

        ZatcaSimplifiedHeader saved = zatcaSimplifiedHeaderRepository.save(header);
        IngestionRequestAttributes.stashDocument(saved.getId(),
                IngestionRequestAttributes.DOC_TYPE_ZATCA_SIMPLIFIED);

        Map<String, Object> auditPayload = new HashMap<>();
        auditPayload.put("invoiceNumber", req.invoiceNumber());
        if (req.erpReferenceId() != null) {
            auditPayload.put("erpReferenceId", req.erpReferenceId());
        }
        auditService.record("INGESTED", "ZATCA_SIMPLIFIED", saved.getId().toString(),
                null, auditPayload);

        return new DocumentIngestionResponse(
                saved.getId(),
                saved.getInvoiceNumber(),
                req.erpReferenceId(),
                saved.getStatus().name(),
                "Simplified invoice ingested successfully");
    }

    private List<ZatcaSimplifiedAllowance> buildSimplifiedHeaderAllowances(
            ZatcaSimplifiedInvoiceIngestionRequest req, ZatcaSimplifiedHeader header) {
        if (req.allowances() == null || req.allowances().isEmpty()) {
            return new ArrayList<>();
        }
        return req.allowances().stream()
                .map(ha -> ZatcaSimplifiedAllowance.builder()
                        .header(header)
                        .sequence(ha.sequence())
                        .amount(ha.amount())
                        .baseAmount(ha.baseAmount())
                        .percentage(ha.percentage())
                        .vatCategoryCode(ha.vatCategoryCode())
                        .vatRate(ha.vatRate())
                        .reasonCode(ha.reasonCode())
                        .reason(ha.reason())
                        .build())
                .toList();
    }

    private List<ZatcaSimplifiedLine> buildSimplifiedLines(
            ZatcaSimplifiedInvoiceIngestionRequest req, ZatcaSimplifiedHeader header) {
        List<ZatcaSimplifiedLine> lines = new ArrayList<>();
        for (ZatcaSimplifiedInvoiceIngestionRequest.LineItem li : req.lines()) {
            ZatcaSimplifiedLine line = ZatcaSimplifiedLine.builder()
                    .header(header)
                    .lineNumber(li.lineNumber())
                    .itemCode(li.itemCode())
                    .description(li.description())
                    .unitType(li.unitType())
                    .quantity(li.quantity())
                    .itemNetPrice(li.unitPrice())
                    .itemGrossPrice(li.itemGrossPrice())
                    .itemPriceDiscount(li.itemPriceDiscount())
                    .itemPriceBaseQuantity(li.itemPriceBaseQuantity() != null
                            ? li.itemPriceBaseQuantity() : BigDecimal.ONE)
                    .itemPriceBaseQuantityUnit(li.itemPriceBaseQuantityUnit())
                    .lineExtensionAmount(li.lineExtensionAmount())
                    .netAmount(li.netAmount())
                    .vatCategoryCode(li.vatCategoryCode())
                    .vatRate(li.vatRate())
                    .vatAmount(li.vatAmount())
                    .exemptionReasonCode(li.exemptionReasonCode())
                    .exemptionReasonText(li.exemptionReasonText())
                    .build();

            if (li.allowances() != null) {
                List<ZatcaSimplifiedLineAllowance> lineAllowances = li.allowances().stream()
                        .map(la -> ZatcaSimplifiedLineAllowance.builder()
                                .line(line)
                                .sequence(la.sequence())
                                .amount(la.amount())
                                .baseAmount(la.baseAmount())
                                .percentage(la.percentage())
                                .reason(la.reason())
                                .build())
                        .toList();
                line.setAllowances(lineAllowances);
            }
            lines.add(line);
        }
        return lines;
    }

    private List<ZatcaSimplifiedTaxSubtotal> buildSimplifiedTaxSubtotals(
            ZatcaSimplifiedInvoiceIngestionRequest req, ZatcaSimplifiedHeader header) {
        record GroupKey(String vatCategoryCode, BigDecimal vatRateNormalized) {}
        Map<GroupKey, List<ZatcaSimplifiedInvoiceIngestionRequest.LineItem>> grouped = req.lines().stream()
                .collect(Collectors.groupingBy(
                        li -> new GroupKey(li.vatCategoryCode(),
                                li.vatRate().stripTrailingZeros())));

        List<ZatcaSimplifiedTaxSubtotal> subtotals = new ArrayList<>();
        for (Map.Entry<GroupKey, List<ZatcaSimplifiedInvoiceIngestionRequest.LineItem>> entry : grouped.entrySet()) {
            List<ZatcaSimplifiedInvoiceIngestionRequest.LineItem> items = entry.getValue();
            ZatcaSimplifiedInvoiceIngestionRequest.LineItem first = items.get(0);
            GroupKey key = entry.getKey();

            BigDecimal taxableAmount = items.stream()
                    .map(ZatcaSimplifiedInvoiceIngestionRequest.LineItem::netAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal taxAmount = items.stream()
                    .map(ZatcaSimplifiedInvoiceIngestionRequest.LineItem::vatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            subtotals.add(ZatcaSimplifiedTaxSubtotal.builder()
                    .header(header)
                    .vatCategoryCode(key.vatCategoryCode())
                    .vatRate(key.vatRateNormalized())
                    .taxableAmount(taxableAmount)
                    .taxAmount(taxAmount)
                    .exemptionReasonCode(first.exemptionReasonCode())
                    .exemptionReasonText(first.exemptionReasonText())
                    .build());
        }
        return subtotals;
    }

    private static Map<String, Object> toSimplifiedSellerPartyMap(
            ZatcaSimplifiedInvoiceIngestionRequest.SellerParty seller) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("partyId", seller.partyId());
        if (seller.partyIdScheme() != null) {
            map.put("partyIdScheme", seller.partyIdScheme());
        }
        map.put("vatNumber", seller.vatNumber());
        if (seller.groupVatNumber() != null) {
            map.put("groupVatNumber", seller.groupVatNumber());
        }
        map.put("buildingNumber", seller.buildingNumber());
        if (seller.additionalNumber() != null) {
            map.put("additionalNumber", seller.additionalNumber());
        }
        map.put("postalCode", seller.postalCode());
        if (seller.street() != null) {
            map.put("street", seller.street());
        }
        if (seller.additionalStreetName() != null) {
            map.put("additionalStreetName", seller.additionalStreetName());
        }
        if (seller.plotIdentification() != null) {
            map.put("plotIdentification", seller.plotIdentification());
        }
        map.put("city", seller.city());
        map.put("countryCode", seller.countryCode());
        return map;
    }

    private static Map<String, Object> toSimplifiedBuyerPartyMap(
            ZatcaSimplifiedInvoiceIngestionRequest.BuyerParty buyer) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (buyer.partyId() != null) {
            map.put("partyId", buyer.partyId());
        }
        if (buyer.partyIdScheme() != null) {
            map.put("partyIdScheme", buyer.partyIdScheme());
        }
        if (buyer.vatNumber() != null) {
            map.put("vatNumber", buyer.vatNumber());
        }
        if (buyer.groupVatNumber() != null) {
            map.put("groupVatNumber", buyer.groupVatNumber());
        }
        if (buyer.buildingNumber() != null) {
            map.put("buildingNumber", buyer.buildingNumber());
        }
        if (buyer.additionalNumber() != null) {
            map.put("additionalNumber", buyer.additionalNumber());
        }
        if (buyer.postalCode() != null) {
            map.put("postalCode", buyer.postalCode());
        }
        if (buyer.street() != null) {
            map.put("street", buyer.street());
        }
        if (buyer.additionalStreetName() != null) {
            map.put("additionalStreetName", buyer.additionalStreetName());
        }
        if (buyer.plotIdentification() != null) {
            map.put("plotIdentification", buyer.plotIdentification());
        }
        if (buyer.city() != null) {
            map.put("city", buyer.city());
        }
        if (buyer.countryCode() != null) {
            map.put("countryCode", buyer.countryCode());
        }
        return map;
    }

    private List<ZatcaStandardAllowance> buildHeaderAllowances(
            ZatcaStandardInvoiceIngestionRequest req, ZatcaStandardHeader header) {
        if (req.allowances() == null || req.allowances().isEmpty()) {
            return new ArrayList<>();
        }
        return req.allowances().stream()
                .map(ha -> ZatcaStandardAllowance.builder()
                        .header(header)
                        .sequence(ha.sequence())
                        .amount(ha.amount())
                        .baseAmount(ha.baseAmount())
                        .percentage(ha.percentage())
                        .vatCategoryCode(ha.vatCategoryCode())
                        .vatRate(ha.vatRate())
                        .reasonCode(ha.reasonCode())
                        .reason(ha.reason())
                        .build())
                .toList();
    }

    private List<ZatcaStandardLine> buildStandardLines(
            ZatcaStandardInvoiceIngestionRequest req, ZatcaStandardHeader header) {
        List<ZatcaStandardLine> lines = new ArrayList<>();
        for (LineItem li : req.lines()) {
            ZatcaStandardLine line = ZatcaStandardLine.builder()
                    .header(header)
                    .lineNumber(li.lineNumber())
                    .itemCode(li.itemCode())
                    .description(li.description())
                    .unitType(li.unitType())
                    .quantity(li.quantity())
                    .itemNetPrice(li.unitPrice())
                    .itemGrossPrice(li.itemGrossPrice())
                    .itemPriceDiscount(li.itemPriceDiscount())
                    .itemPriceBaseQuantity(li.itemPriceBaseQuantity() != null
                            ? li.itemPriceBaseQuantity() : BigDecimal.ONE)
                    .itemPriceBaseQuantityUnit(li.itemPriceBaseQuantityUnit())
                    .lineExtensionAmount(li.lineExtensionAmount())
                    .netAmount(li.netAmount())
                    .vatCategoryCode(li.vatCategoryCode())
                    .vatRate(li.vatRate())
                    .vatAmount(li.vatAmount())
                    .exemptionReasonCode(li.exemptionReasonCode())
                    .exemptionReasonText(li.exemptionReasonText())
                    .build();

            if (li.allowances() != null) {
                List<ZatcaStandardLineAllowance> lineAllowances = li.allowances().stream()
                        .map(la -> ZatcaStandardLineAllowance.builder()
                                .line(line)
                                .sequence(la.sequence())
                                .amount(la.amount())
                                .baseAmount(la.baseAmount())
                                .percentage(la.percentage())
                                .reason(la.reason())
                                .build())
                        .toList();
                line.setAllowances(lineAllowances);
            }
            lines.add(line);
        }
        return lines;
    }

    private List<ZatcaStandardTaxSubtotal> buildTaxSubtotals(
            ZatcaStandardInvoiceIngestionRequest req, ZatcaStandardHeader header) {
        record GroupKey(String vatCategoryCode, BigDecimal vatRateNormalized) {}
        Map<GroupKey, List<LineItem>> grouped = req.lines().stream()
                .collect(Collectors.groupingBy(
                        li -> new GroupKey(li.vatCategoryCode(),
                                li.vatRate().stripTrailingZeros())));

        List<ZatcaStandardTaxSubtotal> subtotals = new ArrayList<>();
        for (Map.Entry<GroupKey, List<LineItem>> entry : grouped.entrySet()) {
            List<LineItem> items = entry.getValue();
            LineItem first = items.get(0);
            GroupKey key = entry.getKey();

            BigDecimal taxableAmount = items.stream()
                    .map(LineItem::netAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal taxAmount = items.stream()
                    .map(LineItem::vatAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            subtotals.add(ZatcaStandardTaxSubtotal.builder()
                    .header(header)
                    .vatCategoryCode(key.vatCategoryCode())
                    .vatRate(key.vatRateNormalized())
                    .taxableAmount(taxableAmount)
                    .taxAmount(taxAmount)
                    .exemptionReasonCode(first.exemptionReasonCode())
                    .exemptionReasonText(first.exemptionReasonText())
                    .build());
        }
        return subtotals;
    }

    private void populateTenantContext(CompanyResolutionService.ResolvedContext ctx,
            String environment) {
        TenantContext.set(new TenantContext.Holder(
                gatewayPrincipalId,
                ctx.companyId(),
                ctx.authorityEnvironmentId(),
                "ZATCA",
                environment,
                TenantContext.Mode.OPERATIONAL_MODE,
                false,
                System.currentTimeMillis(),
                null));
        IngestionRequestAttributes.stash(ctx.companyId(), ctx.authorityEnvironmentId());
    }

    private static DocumentState toDocumentState(IntegrationDocumentStatus status) {
        return switch (status) {
            case DRAFT -> DocumentState.DRAFT;
            case VALID -> DocumentState.ACCEPTED;
            case INVALID -> DocumentState.REJECTED;
            case CLEARED -> DocumentState.ACCEPTED;
            case REPORTED -> DocumentState.ACCEPTED;
            case REJECTED -> DocumentState.REJECTED;
            case FAILED -> DocumentState.REJECTED;
            case CANCELLED -> DocumentState.CANCELLED;
        };
    }

    private static Map<String, Object> toSellerPartyMap(
            ZatcaStandardInvoiceIngestionRequest.SellerParty seller) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("partyId", seller.partyId());
        if (seller.partyIdScheme() != null) {
            map.put("partyIdScheme", seller.partyIdScheme());
        }
        map.put("vatNumber", seller.vatNumber());
        if (seller.groupVatNumber() != null) {
            map.put("groupVatNumber", seller.groupVatNumber());
        }
        map.put("buildingNumber", seller.buildingNumber());
        if (seller.additionalNumber() != null) {
            map.put("additionalNumber", seller.additionalNumber());
        }
        map.put("postalCode", seller.postalCode());
        if (seller.street() != null) {
            map.put("street", seller.street());
        }
        if (seller.additionalStreetName() != null) {
            map.put("additionalStreetName", seller.additionalStreetName());
        }
        if (seller.plotIdentification() != null) {
            map.put("plotIdentification", seller.plotIdentification());
        }
        map.put("city", seller.city());
        map.put("countryCode", seller.countryCode());
        return map;
    }

    private static Map<String, Object> toBuyerPartyMap(
            ZatcaStandardInvoiceIngestionRequest.BuyerParty buyer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("partyId", buyer.partyId());
        if (buyer.partyIdScheme() != null) {
            map.put("partyIdScheme", buyer.partyIdScheme());
        }
        map.put("vatNumber", buyer.vatNumber());
        if (buyer.groupVatNumber() != null) {
            map.put("groupVatNumber", buyer.groupVatNumber());
        }
        map.put("buildingNumber", buyer.buildingNumber());
        if (buyer.additionalNumber() != null) {
            map.put("additionalNumber", buyer.additionalNumber());
        }
        map.put("postalCode", buyer.postalCode());
        if (buyer.street() != null) {
            map.put("street", buyer.street());
        }
        if (buyer.additionalStreetName() != null) {
            map.put("additionalStreetName", buyer.additionalStreetName());
        }
        if (buyer.plotIdentification() != null) {
            map.put("plotIdentification", buyer.plotIdentification());
        }
        map.put("city", buyer.city());
        map.put("countryCode", buyer.countryCode());
        return map;
    }
}
