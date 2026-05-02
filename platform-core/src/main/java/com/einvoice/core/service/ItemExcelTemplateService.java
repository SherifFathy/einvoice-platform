package com.einvoice.core.service;

import com.einvoice.core.service.importing.TemplateException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Service for generating item Excel import templates. */
@Service
public class ItemExcelTemplateService {

    static final String[] EXPECTED_HEADERS = {
        "Item Code*", "Name (Arabic)*", "Name (English)*",
        "Unit of Measure*", "Unit Price*", "VAT Category*",
        "VAT Rate (%)*", "Authority Scope*", "Description"
    };

    /** Returns the expected header labels for validation.
     *
     * @return a copy of the expected header labels
     */
    public String[] getExpectedHeaders() {
        return EXPECTED_HEADERS.clone();
    }

    /**
     * Generates an .xlsx template with headers and an example row.
     *
     * @return the template file as a byte array
     */
    @PreAuthorize("hasAuthority('READ')")
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Items");

            String[] headers = {
                "Item Code*", "Name (Arabic)*", "Name (English)*",
                "Unit of Measure*", "Unit Price*", "VAT Category*",
                "VAT Rate (%)*", "Authority Scope*", "Description"
            };

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("SRV-001");
            exampleRow.createCell(1).setCellValue("خدمة استشارية");
            exampleRow.createCell(2).setCellValue("Consulting Service");
            exampleRow.createCell(3).setCellValue("HR");
            exampleRow.createCell(4).setCellValue("500.0000");
            exampleRow.createCell(5).setCellValue("S");
            exampleRow.createCell(6).setCellValue("15.00");
            exampleRow.createCell(7).setCellValue("BOTH");
            exampleRow.createCell(8).setCellValue(
                    "Hourly consulting service");

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new TemplateException("Failed to generate template", e);
        }
    }
}
