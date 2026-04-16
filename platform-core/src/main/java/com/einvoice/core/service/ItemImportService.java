package com.einvoice.core.service;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.domain.enums.VatCategory;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.ItemRepository;
import com.einvoice.core.service.importing.ImportError;
import com.einvoice.core.service.importing.ImportException;
import com.einvoice.core.service.importing.ImportResult;
import java.math.BigDecimal;
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

/** Service for importing items from Excel files with row-level validation. */
@Service
public class ItemImportService {

    private final ItemRepository itemRepository;

    private final CompanyRepository companyRepository;

    public ItemImportService(ItemRepository itemRepository,
            CompanyRepository companyRepository) {
        this.itemRepository = itemRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Imports items from an Excel file. Valid rows are persisted;
     * invalid rows are collected into an error report.
     *
     * @param file the uploaded Excel file
     * @return the import result with counts and per-row errors
     */
    @PreAuthorize("hasAuthority('CREATE')")
    public ImportResult importItems(MultipartFile file) {
        Long companyId = TenantContext.getCurrentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));

        List<ImportError> errors = new ArrayList<>();
        List<Item> validItems = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }
                totalRows++;
                Item item = parseRow(row, i + 1, companyId, codesInFile,
                        errors);
                if (item != null) {
                    item.setCompany(company);
                    validItems.add(item);
                }
            }
        } catch (Exception e) {
            throw new ImportException(
                    "Failed to read Excel file: " + e.getMessage(), e);
        }

        int importedCount = 0;
        for (Item item : validItems) {
            try {
                itemRepository.save(item);
                importedCount++;
            } catch (Exception e) {
                errors.add(new ImportError(0, "persistence",
                        "Failed to save item '" + item.getNameEn() + "': "
                                + e.getMessage()));
            }
        }

        return new ImportResult(totalRows, importedCount, errors.size(),
                errors);
    }

    private Item parseRow(Row row, int rowNum, Long companyId,
            Set<String> codesInFile, List<ImportError> errors) {
        boolean hasError = false;

        String code = getStringValue(row, 0);
        if (code == null || code.isBlank()) {
            errors.add(new ImportError(rowNum, "code", "Code is required"));
            hasError = true;
        }

        String nameEn = getStringValue(row, 1);
        if (nameEn == null || nameEn.isBlank()) {
            errors.add(new ImportError(rowNum, "nameEn",
                    "Name (English) is required"));
            hasError = true;
        }

        String unitOfMeasure = getStringValue(row, 3);
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) {
            errors.add(new ImportError(rowNum, "unitOfMeasure",
                    "Unit of measure is required"));
            hasError = true;
        }

        BigDecimal unitPrice = null;
        String unitPriceStr = getStringValue(row, 4);
        if (unitPriceStr == null || unitPriceStr.isBlank()) {
            errors.add(new ImportError(rowNum, "unitPrice",
                    "Unit price is required"));
            hasError = true;
        } else {
            try {
                unitPrice = new BigDecimal(unitPriceStr);
                if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new ImportError(rowNum, "unitPrice",
                            "Unit price must be positive"));
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                errors.add(new ImportError(rowNum, "unitPrice",
                        "Invalid number: " + unitPriceStr));
                hasError = true;
            }
        }

        VatCategory vatCategory = null;
        String vatCategoryStr = getStringValue(row, 5);
        if (vatCategoryStr == null || vatCategoryStr.isBlank()) {
            errors.add(new ImportError(rowNum, "vatCategory",
                    "VAT category is required: S, Z, E, or O"));
            hasError = true;
        } else {
            try {
                vatCategory = VatCategory.valueOf(
                        vatCategoryStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new ImportError(rowNum, "vatCategory",
                        "Invalid value: must be S, Z, E, or O"));
                hasError = true;
            }
        }

        BigDecimal vatRate = null;
        String vatRateStr = getStringValue(row, 6);
        if (vatRateStr == null || vatRateStr.isBlank()) {
            errors.add(new ImportError(rowNum, "vatRate",
                    "VAT rate is required"));
            hasError = true;
        } else {
            try {
                vatRate = new BigDecimal(vatRateStr);
                if (vatRate.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(new ImportError(rowNum, "vatRate",
                            "VAT rate must be >= 0"));
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                errors.add(new ImportError(rowNum, "vatRate",
                        "Invalid number: " + vatRateStr));
                hasError = true;
            }
        }

        AuthorityScope authorityScope = null;
        String scopeStr = getStringValue(row, 8);
        if (scopeStr != null && !scopeStr.isBlank()) {
            try {
                authorityScope = AuthorityScope.valueOf(
                        scopeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new ImportError(rowNum, "authorityScope",
                        "Invalid value: must be ZATCA, ETA, or BOTH"));
                hasError = true;
            }
        } else {
            authorityScope = AuthorityScope.BOTH;
        }

        if (code != null && !code.isBlank()) {
            String codeLower = code.toLowerCase();
            if (codesInFile.contains(codeLower)) {
                errors.add(new ImportError(rowNum, "code",
                        "Duplicate code in file: " + code));
                hasError = true;
            } else {
                codesInFile.add(codeLower);
                if (itemRepository
                        .existsByCompanyIdAndCodeAndIsActiveTrue(companyId,
                                code)) {
                    errors.add(new ImportError(rowNum, "code",
                            "Item code " + code
                                    + " already exists for this company"));
                    hasError = true;
                }
            }
        }

        if (hasError) {
            return null;
        }

        return Item.builder()
                .code(code)
                .nameEn(nameEn)
                .nameAr(getStringValue(row, 2))
                .unitOfMeasure(unitOfMeasure)
                .unitPrice(unitPrice)
                .vatCategory(vatCategory)
                .vatRate(vatRate)
                .description(getStringValue(row, 7))
                .authorityScope(authorityScope)
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
            return BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
        }
        return null;
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < 9; i++) {
            String val = getStringValue(row, i);
            if (val != null && !val.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
