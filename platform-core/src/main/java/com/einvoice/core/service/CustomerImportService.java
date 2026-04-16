package com.einvoice.core.service;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.service.importing.ImportError;
import com.einvoice.core.service.importing.ImportException;
import com.einvoice.core.service.importing.ImportResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Service for importing customers from Excel files with row-level validation. */
@Service
public class CustomerImportService {

    private final CustomerRepository customerRepository;

    private final CompanyRepository companyRepository;

    /**
     * Creates the customer import service.
     *
     * @param customerRepository the customer repository
     * @param companyRepository the company repository
     */
    public CustomerImportService(CustomerRepository customerRepository,
            CompanyRepository companyRepository) {
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Imports customers from an Excel file. Valid rows are persisted;
     * invalid rows are collected into an error report.
     *
     * @param file the uploaded Excel file
     * @return the import result with counts and per-row errors
     */
    @PreAuthorize("hasAuthority('CREATE')")
    public ImportResult importCustomers(MultipartFile file) {
        Long companyId = TenantContext.getCurrentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));

        List<ImportError> errors = new ArrayList<>();
        List<Customer> validCustomers = new ArrayList<>();
        Set<String> vatNumbersInFile = new HashSet<>();
        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }
                totalRows++;
                Customer customer = parseRow(row, i + 1, companyId,
                        vatNumbersInFile, errors);
                if (customer != null) {
                    customer.setCompany(company);
                    validCustomers.add(customer);
                }
            }
        } catch (Exception e) {
            throw new ImportException(
                    "Failed to read Excel file: " + e.getMessage(), e);
        }

        int importedCount = 0;
        for (Customer c : validCustomers) {
            try {
                customerRepository.save(c);
                importedCount++;
            } catch (Exception e) {
                errors.add(new ImportError(0, "persistence",
                        "Failed to save customer '" + c.getNameEn() + "': "
                                + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, importedCount,
                errors.size(), errors);
    }

    private Customer parseRow(Row row, int rowNum, Long companyId,
            Set<String> vatNumbersInFile, List<ImportError> errors) {
        boolean hasError = false;
        String nameEn = getStringValue(row, 0);
        if (nameEn == null || nameEn.isBlank()) {
            errors.add(new ImportError(rowNum, "nameEn",
                    "Name (English) is required"));
            hasError = true;
        }

        CustomerType customerType = null;
        String customerTypeStr = getStringValue(row, 3);
        if (customerTypeStr == null || customerTypeStr.isBlank()) {
            errors.add(new ImportError(rowNum, "customerType",
                    "Customer type is required: B2B or B2C"));
            hasError = true;
        } else {
            try {
                customerType = CustomerType.valueOf(
                        customerTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new ImportError(rowNum, "customerType",
                        "Invalid value: must be B2B or B2C"));
                hasError = true;
            }
        }

        String vatNumber = getStringValue(row, 2);
        if (customerType == CustomerType.B2B
                && (vatNumber == null || vatNumber.isBlank())) {
            errors.add(new ImportError(rowNum, "vatNumber",
                    "VAT number is required for B2B customers"));
            hasError = true;
        }

        if (vatNumber != null && !vatNumber.isBlank()) {
            String vatLower = vatNumber.toLowerCase();
            if (vatNumbersInFile.contains(vatLower)) {
                errors.add(new ImportError(rowNum, "vatNumber",
                        "Duplicate VAT number in file: " + vatNumber));
                hasError = true;
            } else {
                vatNumbersInFile.add(vatLower);
                if (customerRepository
                        .existsByCompanyIdAndVatNumberAndIsActiveTrue(
                                companyId, vatNumber)) {
                    errors.add(new ImportError(rowNum, "vatNumber",
                            "Duplicate VAT number: " + vatNumber));
                    hasError = true;
                }
            }
        }

        if (hasError) {
            return null;
        }

        String countryCode = getStringValue(row, 11);
        return Customer.builder()
                .nameEn(nameEn)
                .nameAr(getStringValue(row, 1))
                .vatNumber(vatNumber)
                .customerType(customerType)
                .idType(getStringValue(row, 4))
                .idValue(getStringValue(row, 5))
                .street(getStringValue(row, 6))
                .buildingNumber(getStringValue(row, 7))
                .city(getStringValue(row, 8))
                .district(getStringValue(row, 9))
                .postalCode(getStringValue(row, 10))
                .countryCode(countryCode != null && !countryCode.isBlank()
                        ? countryCode : "SA")
                .contactEmail(getStringValue(row, 12))
                .contactPhone(getStringValue(row, 13))
                .isActive(true)
                .build();
    }

    private String getStringValue(Row row, int cellIndex) {
        var cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            String val = cell.getStringCellValue();
            return val.isBlank() ? null : val.trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < 14; i++) {
            String val = getStringValue(row, i);
            if (val != null && !val.isBlank()) {
                return false;
            }
        }
        return true;
    }

}
