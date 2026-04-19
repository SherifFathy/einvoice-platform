package com.einvoice.api.invoice;

import com.einvoice.api.invoice.dto.CreateInvoiceRequest;
import com.einvoice.api.invoice.dto.InvoiceDetailResponse;
import com.einvoice.api.invoice.dto.InvoiceLineRequest;
import com.einvoice.api.invoice.dto.InvoiceLineResponse;
import com.einvoice.api.invoice.dto.InvoiceListResponse;
import com.einvoice.api.invoice.dto.ValidationErrorResponse;
import com.einvoice.api.invoice.dto.VatBreakdownResponse;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.InvoiceVatBreakdown;
import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.service.InvoiceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for draft invoice CRUD operations. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;

    public InvoiceController(InvoiceService invoiceService,
            ObjectMapper objectMapper) {
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists invoices with optional filters.
     *
     * @param pageable pagination parameters
     * @param status optional status filter
     * @param type optional type filter
     * @param authority optional authority filter
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param search optional search term (invoice number)
     * @param buyer optional buyer name search
     * @return page of invoice summaries
     */
    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Page<InvoiceListResponse>> listInvoices(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String authority,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String buyer) {
        InvoiceStatus invoiceStatus = parseStatus(status);
        InvoiceType invoiceType = parseType(type);
        Authority invoiceAuthority = parseAuthority(authority);
        Page<Invoice> invoices = invoiceService.list(invoiceStatus,
                invoiceType, invoiceAuthority, dateFrom, dateTo, search,
                buyer, pageable);
        return ResponseEntity.ok(invoices.map(this::toListResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<InvoiceDetailResponse> getInvoice(
            @PathVariable UUID id) {
        Invoice invoice = invoiceService.getDetail(id);
        return ResponseEntity.ok(toDetailResponse(invoice));
    }

    /**
     * Creates a new draft invoice.
     *
     * @param request the invoice creation request
     * @return the created invoice or validation errors
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<?> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request) {
        Invoice invoice = toEntity(request);
        try {
            Invoice saved = invoiceService.createDraft(invoice);
            return ResponseEntity
                    .created(URI.create("/api/invoices/" + saved.getId()))
                    .body(toDetailResponse(saved));
        } catch (InvoiceService.InvoiceValidationException e) {
            List<ValidationErrorResponse> errors = e.getErrors().stream()
                    .map(err -> new ValidationErrorResponse(err.getField(),
                            err.getMessage()))
                    .toList();
            return ResponseEntity.badRequest()
                    .body(Map.of("errors", errors));
        }
    }

    /**
     * Updates a draft invoice.
     *
     * @param id the invoice identifier
     * @param request the invoice update request
     * @return the updated invoice or validation errors
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<?> updateInvoice(@PathVariable UUID id,
            @Valid @RequestBody CreateInvoiceRequest request) {
        Invoice updates = toEntity(request);
        try {
            Invoice updated = invoiceService.updateDraft(id, updates);
            return ResponseEntity.ok(toDetailResponse(updated));
        } catch (InvoiceService.InvoiceValidationException e) {
            List<ValidationErrorResponse> errors = e.getErrors().stream()
                    .map(err -> new ValidationErrorResponse(err.getField(),
                            err.getMessage()))
                    .toList();
            return ResponseEntity.badRequest()
                    .body(Map.of("errors", errors));
        } catch (InvoiceService.InvoiceNotDraftException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cancels a draft invoice.
     *
     * @param id the invoice identifier
     * @return no content or error
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<?> cancelInvoice(@PathVariable UUID id) {
        try {
            invoiceService.cancelDraft(id);
            return ResponseEntity.noContent().build();
        } catch (InvoiceService.InvoiceNotDraftException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private Invoice toEntity(CreateInvoiceRequest req) {
        InvoiceType invoiceType;
        try {
            invoiceType = InvoiceType.valueOf(req.type());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid invoice type: " + req.type());
        }

        Authority authority;
        try {
            authority = Authority.valueOf(req.authority());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid authority: " + req.authority());
        }

        String envStr = com.einvoice.core.context.EnvironmentContext
                .getCurrentEnvironment();
        if (envStr == null || envStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No active environment selected. "
                            + "Please select an environment first.");
        }
        Environment environment;
        try {
            environment = Environment.valueOf(envStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid environment: " + envStr);
        }

        Invoice invoice = Invoice.builder()
                .type(invoiceType)
                .issueDate(req.issueDate())
                .supplyDate(req.supplyDate())
                .supplyEndDate(req.supplyEndDate())
                .currency(req.currency())
                .authority(authority)
                .environment(environment)
                .paymentMeansCode(req.paymentMeansCode())
                .paymentTerms(req.paymentTerms())
                .prepaidAmount(req.prepaidAmount())
                .totalAllowances(req.totalAllowances())
                .notes(req.notes())
                .externalInvoiceReference(req.externalInvoiceReference())
                .build();

        if (req.subtypeFlags() != null) {
            try {
                invoice.setSubtypeFlags(
                        objectMapper.writeValueAsString(req.subtypeFlags()));
            } catch (Exception e) {
                invoice.setSubtypeFlags("{}");
            }
        }

        if (req.buyerId() != null) {
            Customer buyer = new Customer();
            buyer.setId(req.buyerId());
            invoice.setBuyer(buyer);
        }

        if (req.branchId() != null) {
            Branch branch = new Branch();
            branch.setId(req.branchId());
            invoice.setBranch(branch);
        }

        if (req.originalInvoiceId() != null) {
            Invoice original = new Invoice();
            original.setId(req.originalInvoiceId());
            invoice.setOriginalInvoice(original);
        }

        if (req.lines() != null) {
            List<InvoiceLine> lines = req.lines().stream()
                    .map(this::toLineEntity)
                    .toList();
            invoice.setLines(lines);
        }

        return invoice;
    }

    private InvoiceLine toLineEntity(InvoiceLineRequest req) {
        InvoiceLine line = InvoiceLine.builder()
                .descriptionEn(req.descriptionEn())
                .descriptionAr(req.descriptionAr())
                .quantity(req.quantity())
                .unit(req.unit())
                .unitPrice(req.unitPrice())
                .discountAmount(req.discountAmount())
                .vatCategory(req.vatCategory())
                .vatRate(req.vatRate())
                .sortOrder(req.sortOrder())
                .build();
        if (req.itemId() != null) {
            Item item = new Item();
            item.setId(req.itemId());
            line.setItem(item);
        }
        return line;
    }

    private InvoiceListResponse toListResponse(Invoice inv) {
        String buyerName = inv.getBuyer() != null
                ? inv.getBuyer().getNameEn() : null;
        return new InvoiceListResponse(inv.getId(),
                inv.getInvoiceNumber(),
                inv.getType().name(),
                inv.getStatus().name(),
                inv.getIssueDate(),
                buyerName,
                inv.getTotalWithVat(),
                inv.getAmountDue(),
                inv.getAuthority().name(),
                inv.getCreatedAt());
    }

    private InvoiceDetailResponse toDetailResponse(Invoice inv) {
        final List<InvoiceLineResponse> lineResponses = inv.getLines().stream()
                .map(this::toLineResponse)
                .toList();

        final List<VatBreakdownResponse> vatResponses =
                inv.getVatBreakdown().stream()
                        .map(this::toVatBreakdownResponse)
                        .toList();

        Map<String, Boolean> subtypeFlags = Collections.emptyMap();
        if (inv.getSubtypeFlags() != null
                && !inv.getSubtypeFlags().isBlank()
                && !"{}".equals(inv.getSubtypeFlags())) {
            try {
                subtypeFlags = objectMapper.readValue(inv.getSubtypeFlags(),
                        new TypeReference<Map<String, Boolean>>() {});
            } catch (Exception e) {
                subtypeFlags = Collections.emptyMap();
            }
        }

        Map<String, Object> buyerData = Collections.emptyMap();
        if (inv.getBuyerData() != null
                && !inv.getBuyerData().isBlank()
                && !"{}".equals(inv.getBuyerData())) {
            try {
                buyerData = objectMapper.readValue(inv.getBuyerData(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                buyerData = Collections.emptyMap();
            }
        }

        Map<String, Object> sellerData = Collections.emptyMap();
        if (inv.getSellerData() != null
                && !inv.getSellerData().isBlank()
                && !"{}".equals(inv.getSellerData())) {
            try {
                sellerData = objectMapper.readValue(inv.getSellerData(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                sellerData = Collections.emptyMap();
            }
        }

        return new InvoiceDetailResponse(inv.getId(),
                inv.getInvoiceNumber(),
                inv.getType().name(),
                subtypeFlags,
                inv.getStatus().name(),
                inv.getIssueDate(),
                inv.getSupplyDate(),
                inv.getSupplyEndDate(),
                inv.getCurrency(),
                inv.getBuyer() != null ? inv.getBuyer().getId() : null,
                inv.getBuyer() != null ? inv.getBuyer().getNameEn() : null,
                buyerData,
                sellerData,
                inv.getPaymentMeansCode(),
                inv.getPaymentTerms(),
                inv.getPrepaidAmount(),
                inv.getTotalLineNet(),
                inv.getTotalAllowances(),
                inv.getTotalWithoutVat(),
                inv.getTotalVat(),
                inv.getTotalWithVat(),
                inv.getAmountDue(),
                inv.getAuthority().name(),
                inv.getEnvironment().name(),
                inv.getBranch() != null ? inv.getBranch().getId() : null,
                inv.getOriginalInvoice() != null
                        ? inv.getOriginalInvoice().getId() : null,
                inv.getExternalInvoiceReference(),
                inv.getNotes(),
                inv.getCreatedAt(),
                lineResponses,
                vatResponses);
    }

    private InvoiceLineResponse toLineResponse(InvoiceLine line) {
        return new InvoiceLineResponse(line.getId(),
                line.getItem() != null ? line.getItem().getId() : null,
                line.getDescriptionEn(),
                line.getDescriptionAr(),
                line.getQuantity(),
                line.getUnit(),
                line.getUnitPrice(),
                line.getDiscountAmount(),
                line.getVatCategory(),
                line.getVatRate(),
                line.getLineNetAmount(),
                line.getLineVatAmount(),
                line.getLineTotal(),
                line.getSortOrder());
    }

    private VatBreakdownResponse toVatBreakdownResponse(
            InvoiceVatBreakdown bd) {
        return new VatBreakdownResponse(bd.getVatCategoryCode(),
                bd.getVatRate(), bd.getTaxableAmount(), bd.getTaxAmount());
    }

    private InvoiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status: " + status);
        }
    }

    private InvoiceType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return InvoiceType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid type: " + type);
        }
    }

    private Authority parseAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        try {
            return Authority.valueOf(authority.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid authority: " + authority);
        }
    }
}
