package com.einvoice.core.service;

import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.domain.enums.VatCategory;
import com.einvoice.core.service.importing.ExcelParseResult;
import com.einvoice.core.service.importing.ImportException;
import com.einvoice.core.service.importing.ParsedRow;
import com.einvoice.core.service.importing.RowError;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Parses item Excel files into validated entity rows. No persistence. */
@Service
public class ItemImportService {

    private final ItemExcelTemplateService templateService;

    /**
     * Creates the item import service.
     *
     * @param templateService the item Excel template service for header definitions
     */
    public ItemImportService(ItemExcelTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Parses an uploaded Excel file into validated item entities.
     * Validates headers against the expected template and performs
     * row-level validation (required fields, numeric ranges, duplicates).
     * Does NOT persist anything.
     *
     * @param file the uploaded Excel file
     * @return parsed result with valid rows and per-row errors
     */
    public ExcelParseResult<Item> parseExcel(MultipartFile file) {
        List<RowError> errors = new ArrayList<>();
        List<ParsedRow<Item>> validRows = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        int totalRows = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            validateHeaders(sheet);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }
                totalRows++;
                int rowNum = i + 1;
                Item item = parseRow(row, rowNum, codesInFile, errors);
                if (item != null) {
                    validRows.add(new ParsedRow<>(rowNum, item));
                }
            }
        } catch (ImportException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException(
                    "Failed to read Excel file: " + e.getMessage(), e);
        }

        return new ExcelParseResult<>(totalRows, validRows, errors);
    }

    private void validateHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new ImportException("Template has no header row", null);
        }
        String[] expected = templateService.getExpectedHeaders();
        for (int i = 0; i < expected.length; i++) {
            String actual = getCellStringValue(headerRow, i);
            if (actual == null || !normalizeHeader(actual)
                    .equals(normalizeHeader(expected[i]))) {
                throw new ImportException(
                        "Header mismatch at column " + (i + 1)
                                + ": expected '" + expected[i]
                                + "', got '"
                                + (actual != null ? actual : "(empty)") + "'",
                        null);
            }
        }
    }

    private String normalizeHeader(String header) {
        return header.trim().toLowerCase().replaceAll("[*\\s()/%]", "");
    }

    private Item parseRow(Row row, int rowNum, Set<String> codesInFile,
            List<RowError> errors) {
        boolean hasError = false;

        String code = getStringValue(row, 0);
        if (code == null || code.isBlank()) {
            errors.add(new RowError(rowNum, "code", "Item Code is required"));
            hasError = true;
        }

        final String nameAr = getStringValue(row, 1);

        final String nameEn = getStringValue(row, 2);
        if (nameEn == null || nameEn.isBlank()) {
            errors.add(new RowError(rowNum, "nameEn",
                    "Name (English) is required"));
            hasError = true;
        }

        String unitOfMeasure = getStringValue(row, 3);
        if (unitOfMeasure == null || unitOfMeasure.isBlank()) {
            errors.add(new RowError(rowNum, "unitOfMeasure",
                    "Unit of measure is required"));
            hasError = true;
        }

        BigDecimal unitPrice = null;
        String unitPriceStr = getStringValue(row, 4);
        if (unitPriceStr == null || unitPriceStr.isBlank()) {
            errors.add(new RowError(rowNum, "unitPrice",
                    "Unit price is required"));
            hasError = true;
        } else {
            try {
                unitPrice = new BigDecimal(unitPriceStr);
                if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new RowError(rowNum, "unitPrice",
                            "Unit price must be positive"));
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                errors.add(new RowError(rowNum, "unitPrice",
                        "Invalid number: " + unitPriceStr));
                hasError = true;
            }
        }

        VatCategory vatCategory = null;
        String vatCategoryStr = getStringValue(row, 5);
        if (vatCategoryStr == null || vatCategoryStr.isBlank()) {
            errors.add(new RowError(rowNum, "vatCategory",
                    "VAT category is required: S, Z, E, or O"));
            hasError = true;
        } else {
            try {
                vatCategory = VatCategory.valueOf(
                        vatCategoryStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(rowNum, "vatCategory",
                        "Invalid value: must be S, Z, E, or O"));
                hasError = true;
            }
        }

        BigDecimal vatRate = null;
        String vatRateStr = getStringValue(row, 6);
        if (vatRateStr == null || vatRateStr.isBlank()) {
            errors.add(new RowError(rowNum, "vatRate",
                    "VAT rate is required"));
            hasError = true;
        } else {
            try {
                vatRate = new BigDecimal(vatRateStr);
                if (vatRate.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(new RowError(rowNum, "vatRate",
                            "VAT rate must be >= 0"));
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                errors.add(new RowError(rowNum, "vatRate",
                        "Invalid number: " + vatRateStr));
                hasError = true;
            }
        }

        AuthorityScope authorityScope = null;
        String scopeStr = getStringValue(row, 7);
        if (scopeStr != null && !scopeStr.isBlank()) {
            try {
                authorityScope = AuthorityScope.valueOf(
                        scopeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(rowNum, "authorityScope",
                        "Invalid value: must be ZATCA, ETA, or BOTH"));
                hasError = true;
            }
        } else {
            authorityScope = AuthorityScope.BOTH;
        }

        if (code != null && !code.isBlank()) {
            String codeLower = code.toLowerCase();
            if (codesInFile.contains(codeLower)) {
                errors.add(new RowError(rowNum, "code",
                        "Duplicate code in file: " + code));
                hasError = true;
            } else {
                codesInFile.add(codeLower);
            }
        }

        if (hasError) {
            return null;
        }

        return Item.builder()
                .code(code)
                .nameEn(nameEn)
                .nameAr(nameAr)
                .unitOfMeasure(unitOfMeasure)
                .unitPrice(unitPrice)
                .vatCategory(vatCategory)
                .vatRate(vatRate)
                .description(getStringValue(row, 8))
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

    private String getCellStringValue(Row row, int cellIndex) {
        var cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        return getStringValue(row, cellIndex);
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
