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

/** Service for generating customer Excel import templates. */
@Service
public class CustomerExcelTemplateService {

    /**
     * Generates an .xlsx template with headers and an example row.
     *
     * @return the template file as a byte array
     */
    @PreAuthorize("hasAuthority('READ')")
    public byte[] generateTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Customers");

            String[] headers = {
                "Name (English)*", "Name (Arabic)", "VAT Number",
                "Customer Type* (B2B/B2C)", "ID Type", "ID Value",
                "Street", "Building Number", "City", "District",
                "Postal Code", "Country Code", "Contact Email",
                "Contact Phone"
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
            exampleRow.createCell(0).setCellValue("Ahmed Trading");
            exampleRow.createCell(1).setCellValue(
                    "\u0623\u062D\u0645\u062F \u0644\u0644\u062A\u062C\u0627\u0631\u0629"); // Arabic name
            exampleRow.createCell(2).setCellValue("310000000000003");
            exampleRow.createCell(3).setCellValue("B2B");
            exampleRow.createCell(4).setCellValue("CRN");
            exampleRow.createCell(5).setCellValue("1010000000");
            exampleRow.createCell(6).setCellValue("King Fahd Road");
            exampleRow.createCell(7).setCellValue("5678");
            exampleRow.createCell(8).setCellValue("Riyadh");
            exampleRow.createCell(9).setCellValue("Malaz");
            exampleRow.createCell(10).setCellValue("12345");
            exampleRow.createCell(11).setCellValue("SA");
            exampleRow.createCell(12).setCellValue("info@ahmed.com");
            exampleRow.createCell(13).setCellValue("+966501234567");

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new TemplateException("Failed to generate template", e);
        }
    }
}
