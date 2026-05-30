package com.einvoice.api.integration.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.integration.dto.eta.EtaInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.eta.EtaReceiptIngestionRequest;
import com.einvoice.api.integration.dto.shared.DocumentIngestionResponse;
import com.einvoice.api.integration.dto.shared.IntegrationDocumentStatus;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaInvoiceLine;
import com.einvoice.core.domain.eta.EtaInvoiceLineTax;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.EtaReceiptLine;
import com.einvoice.core.domain.eta.EtaReceiptLineTax;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.BuyerIdentityRequiredException;
import com.einvoice.core.error.DuplicateInvoiceNumberException;
import com.einvoice.core.error.DuplicateReceiptNumberException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.security.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EtaIngestionService {

    private final CompanyResolutionService companyResolutionService;
    private final EtaReceiptHeaderRepository etaReceiptHeaderRepository;
    private final EtaInvoiceHeaderRepository etaInvoiceHeaderRepository;
    private final AuditService auditService;
    private final UUID gatewayPrincipalId;

    public EtaIngestionService(CompanyResolutionService companyResolutionService,
            EtaReceiptHeaderRepository etaReceiptHeaderRepository,
            EtaInvoiceHeaderRepository etaInvoiceHeaderRepository,
            AuditService auditService,
            @Value("${einvoice.integration.gateway-principal-id}") UUID gatewayPrincipalId) {
        this.companyResolutionService = companyResolutionService;
        this.etaReceiptHeaderRepository = etaReceiptHeaderRepository;
        this.etaInvoiceHeaderRepository = etaInvoiceHeaderRepository;
        this.auditService = auditService;
        this.gatewayPrincipalId = gatewayPrincipalId;
    }

    public DocumentIngestionResponse ingestReceipt(EtaReceiptIngestionRequest req) {
        CompanyResolutionService.ResolvedContext ctx = companyResolutionService.resolve(
                req.companyRegistrationNumber(), "ETA", req.environment().name());

        populateTenantContext(ctx, req.environment().name());
        return doIngestReceipt(req, ctx);
    }

    public DocumentIngestionResponse ingestInvoice(EtaInvoiceIngestionRequest req) {
        CompanyResolutionService.ResolvedContext ctx = companyResolutionService.resolve(
                req.companyRegistrationNumber(), "ETA", req.environment().name());

        populateTenantContext(ctx, req.environment().name());
        return doIngestInvoice(req, ctx);
    }

    private DocumentIngestionResponse doIngestInvoice(EtaInvoiceIngestionRequest req,
            CompanyResolutionService.ResolvedContext ctx) {

        etaInvoiceHeaderRepository
                .findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
                        ctx.companyId(), ctx.authorityEnvironmentId(), req.invoiceNumber())
                .ifPresent(existing -> {
                    throw new DuplicateInvoiceNumberException(
                            "Invoice '" + req.invoiceNumber()
                                    + "' already exists for this company and environment.",
                            ctx.companyId(), req.invoiceNumber());
                });

        EtaInvoiceDocumentType docType = EtaInvoiceDocumentType
                .valueOf(req.documentType().toLowerCase());

        if (docType.requiresOriginalDocument() && req.originalInvoiceNumber() == null) {
            throw new MissingOriginalDocumentException(
                    "Document type " + req.documentType()
                            + " requires originalInvoiceNumber",
                    null, req.documentType());
        }

        UUID originalDocumentId = null;
        if (req.originalInvoiceNumber() != null) {
            originalDocumentId = etaInvoiceHeaderRepository
                    .findByCompanyIdAndAuthorityEnvironmentIdAndInvoiceNumber(
                            ctx.companyId(), ctx.authorityEnvironmentId(),
                            req.originalInvoiceNumber())
                    .map(EtaInvoiceHeader::getId)
                    .orElse(null);
        }

        EtaInvoiceHeader header = EtaInvoiceHeader.builder()
                .companyId(ctx.companyId())
                .authorityEnvironmentId(ctx.authorityEnvironmentId())
                .invoiceNumber(req.invoiceNumber())
                .documentType(docType)
                .documentTypeVersion(req.documentTypeVersion())
                .issueDatetime(req.dateTimeIssued())
                .serviceDeliveryDate(req.serviceDeliveryDate())
                .sellerData(toTaxpayerMap(req.seller()))
                .buyerData(toTaxpayerMap(req.buyer()))
                .taxpayerActivityCode(req.taxpayerActivityCode())
                .purchaseOrderReference(req.purchaseOrderReference())
                .salesOrderReference(req.salesOrderReference())
                .proformaInvoiceNumber(req.proformaInvoiceNumber())
                .currency(req.currency())
                .totalSalesAmount(req.totalSalesAmount())
                .totalDiscountAmount(req.totalDiscountAmount())
                .extraDiscountAmount(req.extraDiscountAmount())
                .totalItemsDiscountAmount(req.totalItemsDiscountAmount())
                .netAmount(req.netAmount())
                .totalAmount(req.totalAmount())
                .etaUuid(req.etaUuid())
                .etaLongId(req.etaLongId())
                .etaSubmissionId(req.etaSubmissionId())
                .originalInvoiceNumber(req.originalInvoiceNumber())
                .originalDocumentId(originalDocumentId)
                .erpReferenceId(req.erpReferenceId())
                .state(toDocumentState(req.status()))
                .createdBy(gatewayPrincipalId)
                .build();

        List<EtaInvoiceLine> lines = buildInvoiceLines(req, header);
        header.setLines(lines);

        EtaInvoiceHeader saved = etaInvoiceHeaderRepository.save(header);

        auditService.record("INGESTED", "ETA_INVOICE", saved.getId().toString(),
                null, Map.of("invoiceNumber", req.invoiceNumber(),
                        "erpReferenceId", req.erpReferenceId() != null
                                ? req.erpReferenceId() : ""));

        return new DocumentIngestionResponse(
                saved.getId(),
                saved.getInvoiceNumber(),
                req.erpReferenceId(),
                saved.getState().name(),
                "Invoice ingested successfully");
    }

    private List<EtaInvoiceLine> buildInvoiceLines(EtaInvoiceIngestionRequest req,
            EtaInvoiceHeader header) {
        List<EtaInvoiceLine> lines = new ArrayList<>();
        for (EtaInvoiceIngestionRequest.InvoiceLine il : req.lines()) {
            EtaInvoiceLine line = EtaInvoiceLine.builder()
                    .header(header)
                    .lineNumber(il.lineNumber())
                    .internalCode(il.internalCode())
                    .itemType(il.itemType())
                    .itemCode(il.itemCode())
                    .description(il.description())
                    .unitType(il.unitType())
                    .quantity(il.quantity())
                    .unitValue(toUnitValueMap(il.unitValue()))
                    .salesTotal(il.salesTotal())
                    .discountRate(il.discountRate())
                    .discountAmount(il.discountAmount())
                    .itemsDiscount(il.itemsDiscount())
                    .valueDifference(il.valueDifference())
                    .totalTaxableFees(il.totalTaxableFees())
                    .netTotal(il.netTotal())
                    .taxAmount(il.taxAmount())
                    .total(il.total())
                    .build();

            if (il.taxableItems() != null) {
                List<EtaInvoiceLineTax> taxes = il.taxableItems().stream()
                        .map(lt -> EtaInvoiceLineTax.builder()
                                .line(line)
                                .taxType(lt.taxType())
                                .subType(lt.subType())
                                .taxRate(lt.rate())
                                .taxAmount(lt.amount())
                                .build())
                        .toList();
                line.setTaxes(taxes);
            }
            lines.add(line);
        }
        return lines;
    }

    private DocumentIngestionResponse doIngestReceipt(EtaReceiptIngestionRequest req,
            CompanyResolutionService.ResolvedContext ctx) {

        etaReceiptHeaderRepository
                .findByCompanyIdAndAuthorityEnvironmentIdAndReceiptNumber(
                        ctx.companyId(), ctx.authorityEnvironmentId(),
                        req.header().receiptNumber())
                .ifPresent(existing -> {
                    throw new DuplicateReceiptNumberException(
                            "Receipt '" + req.header().receiptNumber()
                                    + "' already exists for this company and environment.",
                            ctx.companyId(), req.header().receiptNumber());
                });

        validateCrossFieldRules(req);

        EtaReceiptHeader header = buildHeader(req, ctx);
        List<EtaReceiptLine> lines = buildLines(req, header);
        header.setLines(lines);

        EtaReceiptHeader saved = etaReceiptHeaderRepository.save(header);

        auditService.record("INGESTED", "ETA_RECEIPT", saved.getId().toString(),
                null, Map.of("receiptNumber", req.header().receiptNumber(),
                        "erpReferenceId", req.erpReferenceId() != null
                                ? req.erpReferenceId() : ""));

        return new DocumentIngestionResponse(
                saved.getId(),
                saved.getReceiptNumber(),
                req.erpReferenceId(),
                saved.getState().name(),
                "Receipt ingested successfully");
    }

    private void validateCrossFieldRules(EtaReceiptIngestionRequest req) {
        if (req.buyer() != null) {
            if ("B".equals(req.buyer().type())) {
                if (req.buyer().id() == null || req.buyer().id().isBlank()
                        || req.buyer().name() == null || req.buyer().name().isBlank()) {
                    throw new BuyerIdentityRequiredException("B",
                            "buyer id and name are required for type B");
                }
            }
            if ("P".equals(req.buyer().type())
                    && req.totalAmount() != null
                    && req.totalAmount().compareTo(new BigDecimal("150000")) >= 0) {
                if (req.buyer().id() == null || req.buyer().id().isBlank()
                        || req.buyer().name() == null || req.buyer().name().isBlank()) {
                    throw new BuyerIdentityRequiredException("P",
                            "buyer id and name are required when totalAmount >= 150000");
                }
            }
        }
    }

    private EtaReceiptHeader buildHeader(EtaReceiptIngestionRequest req,
            CompanyResolutionService.ResolvedContext ctx) {

        EtaReceiptDocumentType docType = EtaReceiptDocumentType
                .valueOf(req.documentType().receiptType());

        BigDecimal extraDiscount = req.extraReceiptDiscountData() != null
                ? req.extraReceiptDiscountData().stream()
                        .map(EtaReceiptIngestionRequest.Discount::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;

        return EtaReceiptHeader.builder()
                .companyId(ctx.companyId())
                .authorityEnvironmentId(ctx.authorityEnvironmentId())
                .receiptNumber(req.header().receiptNumber())
                .documentType(docType)
                .documentTypeVersion(req.documentType().typeVersion())
                .issueDatetime(OffsetDateTime.parse(req.header().dateTimeIssued()))
                .sellerData(toSellerMap(req.seller()))
                .buyerData(req.buyer() != null ? toBuyerMap(req.buyer()) : null)
                .posSerial(req.seller().deviceSerialNumber())
                .paymentMethod(req.paymentMethod())
                .currency(req.header().currency() != null ? req.header().currency() : "EGP")
                .totalSalesAmount(req.totalSales() != null ? req.totalSales() : BigDecimal.ZERO)
                .totalCommercialDiscount(req.totalCommercialDiscount() != null
                        ? req.totalCommercialDiscount() : BigDecimal.ZERO)
                .extraDiscountAmount(extraDiscount)
                .totalItemsDiscountAmount(req.totalItemsDiscount() != null
                        ? req.totalItemsDiscount() : BigDecimal.ZERO)
                .netAmount(req.netAmount() != null ? req.netAmount() : BigDecimal.ZERO)
                .totalAmount(req.totalAmount() != null ? req.totalAmount() : BigDecimal.ZERO)
                .exchangeRate(req.header().exchangeRate())
                .previousUuid(req.header().previousUUID())
                .referenceOldUuid(req.header().referenceOldUUID())
                .sOrderNameCode(req.header().sOrderNameCode())
                .orderDeliveryMode(req.header().orderDeliveryMode())
                .grossWeight(req.header().grossWeight())
                .netWeight(req.header().netWeight())
                .taxTotals(toTaxTotalsMap(req.taxTotals()))
                .extraReceiptDiscountData(toDiscountListMap(req.extraReceiptDiscountData()))
                .contractorData(req.contractor() != null ? toContractorMap(req.contractor()) : null)
                .beneficiaryData(req.beneficiary() != null ? toBeneficiaryMap(req.beneficiary()) : null)
                .erpReferenceId(req.erpReferenceId())
                .etaReceiptUuid(req.header().uuid())
                .state(toDocumentState(req.status()))
                .createdBy(gatewayPrincipalId)
                .build();
    }

    private List<EtaReceiptLine> buildLines(EtaReceiptIngestionRequest req,
            EtaReceiptHeader header) {
        List<EtaReceiptLine> lines = new ArrayList<>();
        if (req.itemData() == null) {
            return lines;
        }
        for (int i = 0; i < req.itemData().size(); i++) {
            EtaReceiptIngestionRequest.ItemData item = req.itemData().get(i);
            EtaReceiptLine line = EtaReceiptLine.builder()
                    .header(header)
                    .lineNumber(i + 1)
                    .internalCode(item.internalCode())
                    .itemType(item.itemType())
                    .itemCode(item.itemCode())
                    .description(item.description())
                    .unitType(item.unitType())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .salesTotal(item.salesTotal() != null ? item.salesTotal() : BigDecimal.ZERO)
                    .valueDifference(item.valueDifference() != null ? item.valueDifference() : BigDecimal.ZERO)
                    .totalTaxableFees(item.totalTaxableFees() != null ? item.totalTaxableFees() : BigDecimal.ZERO)
                    .netTotal(item.netTotal() != null ? item.netTotal() : BigDecimal.ZERO)
                    .taxAmount(item.taxAmount() != null ? item.taxAmount() : BigDecimal.ZERO)
                    .total(item.total() != null ? item.total() : BigDecimal.ZERO)
                    .build();

            if (item.taxableItems() != null) {
                List<EtaReceiptLineTax> taxes = item.taxableItems().stream()
                        .map(ti -> EtaReceiptLineTax.builder()
                                .line(line)
                                .taxType(ti.taxType())
                                .subType(ti.subType())
                                .taxRate(ti.rate())
                                .taxAmount(ti.amount())
                                .build())
                        .toList();
                line.setTaxes(taxes);
            }
            lines.add(line);
        }
        return lines;
    }

    private void populateTenantContext(CompanyResolutionService.ResolvedContext ctx,
            String environment) {
        TenantContext.set(new TenantContext.Holder(
                gatewayPrincipalId,
                ctx.companyId(),
                ctx.authorityEnvironmentId(),
                "ETA",
                environment,
                TenantContext.Mode.OPERATIONAL_MODE,
                false,
                System.currentTimeMillis(),
                null));
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

    private static Map<String, Object> toSellerMap(EtaReceiptIngestionRequest.Seller seller) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rin", seller.rin());
        map.put("tradeName", seller.tradeName());
        if (seller.branchCode() != null) {
            map.put("branchCode", seller.branchCode());
        }
        map.put("deviceSerialNumber", seller.deviceSerialNumber());
        map.put("activityCode", seller.activityCode());
        if (seller.syndicateLicenseNumber() != null) {
            map.put("syndicateLicenseNumber", seller.syndicateLicenseNumber());
        }
        if (seller.branchAddress() != null) {
            map.put("branchAddress", toBranchAddressMap(seller.branchAddress()));
        }
        return map;
    }

    private static Map<String, Object> toBranchAddressMap(
            EtaReceiptIngestionRequest.BranchAddress addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (addr.country() != null) map.put("country", addr.country());
        if (addr.governate() != null) map.put("governate", addr.governate());
        if (addr.regionCity() != null) map.put("regionCity", addr.regionCity());
        if (addr.street() != null) map.put("street", addr.street());
        if (addr.building() != null) map.put("building", addr.building());
        if (addr.postalCode() != null) map.put("postalCode", addr.postalCode());
        if (addr.floor() != null) map.put("floor", addr.floor());
        if (addr.room() != null) map.put("room", addr.room());
        if (addr.landmark() != null) map.put("landmark", addr.landmark());
        return map;
    }

    private static Map<String, Object> toBuyerMap(EtaReceiptIngestionRequest.Buyer buyer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", buyer.type());
        if (buyer.id() != null) map.put("id", buyer.id());
        if (buyer.name() != null) map.put("name", buyer.name());
        return map;
    }

    private static Map<String, Object> toTaxTotalsMap(
            List<EtaReceiptIngestionRequest.TaxTotal> taxTotals) {
        if (taxTotals == null || taxTotals.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("taxTotals", taxTotals.stream().map(tt -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taxType", tt.taxType());
            item.put("amount", tt.amount());
            return item;
        }).toList());
        return map;
    }

    private static Map<String, Object> toDiscountListMap(
            List<EtaReceiptIngestionRequest.Discount> discounts) {
        if (discounts == null || discounts.isEmpty()) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("discounts", discounts.stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("amount", d.amount());
            if (d.description() != null) item.put("description", d.description());
            return item;
        }).toList());
        return map;
    }

    private static Map<String, Object> toContractorMap(
            EtaReceiptIngestionRequest.Contractor contractor) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (contractor.name() != null) map.put("name", contractor.name());
        if (contractor.amount() != null) map.put("amount", contractor.amount());
        if (contractor.rate() != null) map.put("rate", contractor.rate());
        return map;
    }

    private static Map<String, Object> toBeneficiaryMap(
            EtaReceiptIngestionRequest.Beneficiary beneficiary) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (beneficiary.amount() != null) map.put("amount", beneficiary.amount());
        if (beneficiary.rate() != null) map.put("rate", beneficiary.rate());
        return map;
    }

    private static Map<String, Object> toTaxpayerMap(
            EtaInvoiceIngestionRequest.TaxpayerParty party) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", party.type());
        map.put("id", party.id());
        map.put("name", party.name());
        if (party.address() != null) {
            map.put("address", toPartyAddressMap(party.address()));
        }
        return map;
    }

    private static Map<String, Object> toPartyAddressMap(
            EtaInvoiceIngestionRequest.PartyAddress addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("country", addr.country());
        map.put("governate", addr.governate());
        map.put("regionCity", addr.regionCity());
        map.put("street", addr.street());
        map.put("buildingNumber", addr.buildingNumber());
        if (addr.postalCode() != null) map.put("postalCode", addr.postalCode());
        if (addr.floor() != null) map.put("floor", addr.floor());
        if (addr.room() != null) map.put("room", addr.room());
        if (addr.landmark() != null) map.put("landmark", addr.landmark());
        if (addr.additionalInformation() != null) {
            map.put("additionalInformation", addr.additionalInformation());
        }
        return map;
    }

    private static Map<String, Object> toUnitValueMap(
            EtaInvoiceIngestionRequest.UnitValue uv) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("currencySold", uv.currencySold());
        map.put("amountEGP", uv.amountEGP());
        map.put("amountSold", uv.amountSold());
        if (uv.currencyExchangeRate() != null) {
            map.put("currencyExchangeRate", uv.currencyExchangeRate());
        }
        return map;
    }
}
