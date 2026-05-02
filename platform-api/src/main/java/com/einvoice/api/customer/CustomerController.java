package com.einvoice.api.customer;

import com.einvoice.api.customer.dto.CustomerRequest;
import com.einvoice.api.customer.dto.CustomerResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.service.CustomerExcelTemplateService;
import com.einvoice.core.service.CustomerImportService;
import com.einvoice.core.service.CustomerService;
import com.einvoice.core.service.importing.BulkUploadResult;
import com.einvoice.core.service.importing.ExcelParseResult;
import com.einvoice.core.service.importing.RowError;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for customer CRUD, import, and template download. */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    private final CustomerImportService customerImportService;

    private final CustomerExcelTemplateService customerExcelTemplateService;

    /**
     * Creates the customer controller.
     *
     * @param customerService the customer service
     * @param customerImportService the customer import service
     * @param customerExcelTemplateService the template service
     */
    public CustomerController(CustomerService customerService,
            CustomerImportService customerImportService,
            CustomerExcelTemplateService customerExcelTemplateService) {
        this.customerService = customerService;
        this.customerImportService = customerImportService;
        this.customerExcelTemplateService = customerExcelTemplateService;
    }

    /**
     * Lists customers with optional search and filter.
     *
     * @param pageable pagination parameters
     * @param search free-text search on name/VAT
     * @param type customer type filter (B2B/B2C)
     * @param vatNumber VAT number filter
     * @return a page of customer responses
     */
    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Page<CustomerResponse>> listCustomers(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String vatNumber) {
        CustomerType customerType = parseCustomerType(type);
        Page<Customer> customers = customerService.list(search, customerType,
                vatNumber, pageable);
        return ResponseEntity.ok(customers.map(this::toResponse));
    }

    /**
     * Creates a new customer.
     *
     * @param request the customer creation request
     * @return the created customer response with HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CustomerRequest request) {
        Customer customer = toEntity(request);
        Customer saved = customerService.create(customer);
        return ResponseEntity
                .created(URI.create("/api/customers/" + saved.getId()))
                .body(toResponse(saved));
    }

    /**
     * Retrieves a customer by ID.
     *
     * @param id the customer identifier
     * @return the customer response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        Customer customer = customerService.getById(id);
        return ResponseEntity.ok(toResponse(customer));
    }

    /**
     * Patches an existing customer. Null fields are ignored.
     *
     * @param id the customer identifier
     * @param request the fields to update
     * @return the updated customer response
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        Customer updates = toEntity(request);
        Customer updated = customerService.update(id, updates);
        return ResponseEntity.ok(toResponse(updated));
    }

    /**
     * Soft-deletes a customer. Returns 409 if referenced by invoices.
     *
     * @param id the customer identifier
     * @return empty response with HTTP 204
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Downloads the Excel import template.
     *
     * @return the .xlsx template as a binary response
     */
    @GetMapping("/template")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] template = customerExcelTemplateService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=customers-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"))
                .body(template);
    }

    /**
     * Bulk-uploads customers from an Excel template.
     * Validates headers, parses rows, then creates/updates via
     * {@link CustomerService#createBatch}.
     *
     * @param file the multipart Excel file
     * @return the bulk upload result
     */
    @PostMapping("/bulk-upload")
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<BulkUploadResult> bulkUploadCustomers(
            @RequestParam("file") MultipartFile file) {
        ExcelParseResult<Customer> parsed =
                customerImportService.parseExcel(file);
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = TenantContext.getLovContextId();

        BulkUploadResult batchResult = customerService.createBatch(
                parsed.validRows(), companyId, lovContextId);

        List<RowError> allErrors = new ArrayList<>(parsed.errors());
        allErrors.addAll(batchResult.errors());
        int totalProcessed = batchResult.processed();
        int totalFailed = parsed.errors().size() + batchResult.failed();

        return ResponseEntity.ok(
                new BulkUploadResult(totalProcessed, totalFailed, allErrors));
    }

    private CustomerType parseCustomerType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return CustomerType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid customer type: " + type
                            + ". Must be B2B or B2C");
        }
    }

    private Customer toEntity(CustomerRequest req) {
        return Customer.builder()
                .nameAr(req.nameAr())
                .nameEn(req.nameEn())
                .vatNumber(req.vatNumber())
                .customerType(req.customerType() != null
                        ? CustomerType.valueOf(req.customerType()) : null)
                .idType(req.idType())
                .idValue(req.idValue())
                .street(req.street())
                .buildingNumber(req.buildingNumber())
                .city(req.city())
                .district(req.district())
                .postalCode(req.postalCode())
                .countryCode(req.countryCode() != null
                        ? req.countryCode() : "SA")
                .contactEmail(req.contactEmail())
                .contactPhone(req.contactPhone())
                .build();
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getNameAr(), c.getNameEn(),
                c.getVatNumber(), c.getIdType(), c.getIdValue(), c.getStreet(),
                c.getBuildingNumber(), c.getCity(), c.getDistrict(),
                c.getPostalCode(), c.getCountryCode(),
                c.getCustomerType().name(), c.getContactEmail(),
                c.getContactPhone(), c.getIsActive(), c.getCreatedAt());
    }
}
