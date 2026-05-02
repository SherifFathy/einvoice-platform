package com.einvoice.core.service;

import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.service.importing.ExcelParseResult;
import com.einvoice.core.service.importing.ImportException;
import com.einvoice.core.service.importing.ParsedRow;
import com.einvoice.core.service.importing.RowError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Parses customer Excel files into validated entity rows. No persistence. */
@Service
public class CustomerImportService {

    private final CustomerExcelTemplateService templateService;

    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * Creates the customer import service.
     *
     * @param templateService the customer Excel template service for header definitions
     */
    public CustomerImportService(
            CustomerExcelTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Parses an uploaded Excel file into validated customer entities.
     * Validates headers and performs row-level validation (required fields,
     * B2B VAT requirement, duplicate detection). Does NOT persist.
     *
     * @param file the uploaded Excel file
     * @return parsed result with valid rows and per-row errors
     */
    public ExcelParseResult<Customer> parseExcel(MultipartFile file) {
        List<RowError> errors = new ArrayList<>();
        List<ParsedRow<Customer>> validRows = new ArrayList<>();
        Set<String> keysInFile = new HashSet<>();
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
                Customer customer = parseRow(row, rowNum, keysInFile, errors);
                if (customer != null) {
                    validRows.add(new ParsedRow<>(rowNum, customer));
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

    private Customer parseRow(Row row, int rowNum, Set<String> keysInFile,
            List<RowError> errors) {
        boolean hasError = false;

        final String nameAr = getStringValue(row, 0);

        final String nameEn = getStringValue(row, 1);
        if (nameEn == null || nameEn.isBlank()) {
            errors.add(new RowError(rowNum, "nameEn",
                    "Name (English) is required"));
            hasError = true;
        }

        final String vatNumber = getRawStringValue(row, 2);

        final String idType = getStringValue(row, 3);
        if (idType == null || idType.isBlank()) {
            errors.add(new RowError(rowNum, "idType",
                    "ID Type is required"));
            hasError = true;
        }

        final String idValue = getStringValue(row, 4);
        if (idValue == null || idValue.isBlank()) {
            errors.add(new RowError(rowNum, "idValue",
                    "ID Value is required"));
            hasError = true;
        }

        CustomerType customerType = null;
        String customerTypeStr = getStringValue(row, 5);
        if (customerTypeStr == null || customerTypeStr.isBlank()) {
            errors.add(new RowError(rowNum, "customerType",
                    "Customer type is required: B2B or B2C"));
            hasError = true;
        } else {
            try {
                customerType = CustomerType.valueOf(
                        customerTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(rowNum, "customerType",
                        "Invalid value: must be B2B or B2C"));
                hasError = true;
            }
        }

        if (customerType == CustomerType.B2B
                && (vatNumber == null || vatNumber.isBlank())) {
            errors.add(new RowError(rowNum, "vatNumber",
                    "VAT number is required for B2B customers"));
            hasError = true;
        }

        final String contactEmail = getStringValue(row, 6);
        final String contactPhone = getStringValue(row, 7);

        String countryCode = getStringValue(row, 8);
        if (countryCode == null || countryCode.isBlank()) {
            countryCode = "SA";
        }

        if (customerType == CustomerType.B2B && vatNumber != null
                && !vatNumber.isBlank()) {
            String key = "vat:" + vatNumber.toLowerCase();
            if (keysInFile.contains(key)) {
                errors.add(new RowError(rowNum, "vatNumber",
                        "Duplicate VAT number in file: " + vatNumber));
                hasError = true;
            } else {
                keysInFile.add(key);
            }
        }
        if (customerType == CustomerType.B2C && contactEmail != null
                && !contactEmail.isBlank()) {
            String key = "email:" + contactEmail.toLowerCase();
            if (keysInFile.contains(key)) {
                errors.add(new RowError(rowNum, "contactEmail",
                        "Duplicate email in file: " + contactEmail));
                hasError = true;
            } else {
                keysInFile.add(key);
            }
        }

        if (hasError) {
            return null;
        }

        return Customer.builder()
                .nameAr(nameAr)
                .nameEn(nameEn)
                .vatNumber(vatNumber)
                .customerType(customerType)
                .idType(idType)
                .idValue(idValue)
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                .countryCode(countryCode)
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
            return dataFormatter.formatCellValue(cell);
        }
        return null;
    }

    private String getRawStringValue(Row row, int cellIndex) {
        var cell = row.getCell(cellIndex);
        if (cell == null) {
            return null;
        }
        String val = dataFormatter.formatCellValue(cell);
        return val.isBlank() ? null : val.trim();
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
