package com.einvoice.api.integration;

/** Test-only fixture payloads for the four ingestion endpoints. Kept terse on purpose. */
final class IngestionPayloads {

    private IngestionPayloads() {
    }

    static String etaReceipt(String receiptNumber, String taxNumber, String env, String status) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-OBS",
                  "header": {
                    "dateTimeIssued": "2026-05-27T14:30:00Z",
                    "receiptNumber": "%s",
                    "uuid": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
                    "currency": "EGP"
                  },
                  "documentType": {"receiptType": "r", "typeVersion": "1.2"},
                  "seller": {
                    "rin": "100200300",
                    "tradeName": "T",
                    "deviceSerialNumber": "POS-1",
                    "activityCode": "4610"
                  },
                  "buyer": {"type": "P"},
                  "paymentMethod": "C",
                  "totalSales": 100.00,
                  "totalCommercialDiscount": 0,
                  "extraReceiptDiscountData": [],
                  "totalItemsDiscount": 0,
                  "netAmount": 100.00,
                  "totalAmount": 114.00,
                  "taxTotals": [{"taxType": "T1", "amount": 14.00}],
                  "itemData": [
                    {
                      "itemCode": "EGS-1",
                      "itemType": "EGS",
                      "description": "Item",
                      "unitType": "EA",
                      "quantity": 1.0,
                      "unitPrice": 100.00,
                      "salesTotal": 100.00,
                      "commercialDiscountData": [],
                      "itemDiscountData": [],
                      "valueDifference": 0,
                      "totalTaxableFees": 0,
                      "netTotal": 100.00,
                      "taxAmount": 14.00,
                      "total": 114.00,
                      "taxableItems": [{"taxType": "T1", "amount": 14.00, "subType": "V009", "rate": 14.0}]
                    }
                  ]
                }""".formatted(taxNumber, env, status, receiptNumber);
    }

    static String etaReceiptMissingNumber(String taxNumber) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "PREPROD",
                  "status": "VALID",
                  "header": {
                    "dateTimeIssued": "2026-05-27T14:30:00Z",
                    "uuid": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
                  },
                  "documentType": {"receiptType": "r", "typeVersion": "1.2"},
                  "seller": {"rin": "100200300", "tradeName": "T", "deviceSerialNumber": "P", "activityCode": "4610"},
                  "paymentMethod": "C",
                  "totalSales": 100,
                  "netAmount": 100,
                  "totalAmount": 114,
                  "itemData": [{"itemCode": "I", "itemType": "EGS", "description": "X", "unitType": "EA", "quantity": 1, "unitPrice": 100}]
                }""".formatted(taxNumber);
    }

    static String etaInvoice(String invoiceNumber, String taxNumber, String env, String status) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-OBS-INV",
                  "invoiceNumber": "%s",
                  "documentType": "I",
                  "documentTypeVersion": "1.0",
                  "dateTimeIssued": "2026-05-27T14:30:00+02:00",
                  "seller": {"type": "B", "id": "100200300", "name": "S",
                    "address": {"country":"EG","governate":"C","regionCity":"N","street":"S","buildingNumber":"1"}},
                  "buyer": {"type": "B", "id": "300200100", "name": "B",
                    "address": {"country":"EG","governate":"G","regionCity":"D","street":"T","buildingNumber":"2"}},
                  "currency": "EGP",
                  "totalSalesAmount": 1000.00,
                  "totalDiscountAmount": 0,
                  "extraDiscountAmount": 0,
                  "totalItemsDiscountAmount": 0,
                  "netAmount": 1000.00,
                  "totalAmount": 1140.00,
                  "etaUuid": "U1", "etaLongId": "L1", "etaSubmissionId": "S1",
                  "lines": [
                    {
                      "lineNumber": 1,
                      "internalCode": "IC1",
                      "itemType": "EGS",
                      "itemCode": "EGS1",
                      "description": "Svc",
                      "unitType": "EA",
                      "quantity": 10.0,
                      "unitValue": {"currencySold": "EGP", "amountEGP": 100.00, "amountSold": 100.00, "currencyExchangeRate": 1.0},
                      "salesTotal": 1000.00,
                      "discountRate": 0,
                      "discountAmount": 0,
                      "itemsDiscount": 0,
                      "valueDifference": 0,
                      "totalTaxableFees": 0,
                      "netTotal": 1000.00,
                      "taxAmount": 140.00,
                      "total": 1140.00,
                      "taxableItems": [{"taxType": "T1", "amount": 140.00, "subType": "V009", "rate": 14.0}]
                    }
                  ]
                }""".formatted(taxNumber, env, status, invoiceNumber);
    }

    static String etaInvoiceMissingNumber(String taxNumber) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "PREPROD",
                  "status": "VALID",
                  "documentType": "I",
                  "documentTypeVersion": "1.0",
                  "dateTimeIssued": "2026-05-27T14:30:00+02:00",
                  "seller": {"type":"B","id":"100200300","name":"S",
                    "address":{"country":"EG","governate":"C","regionCity":"N","street":"S","buildingNumber":"1"}},
                  "buyer": {"type":"B","id":"300200100","name":"B",
                    "address":{"country":"EG","governate":"G","regionCity":"D","street":"T","buildingNumber":"2"}},
                  "currency": "EGP",
                  "totalSalesAmount": 100, "totalDiscountAmount": 0, "extraDiscountAmount": 0,
                  "totalItemsDiscountAmount": 0, "netAmount": 100, "totalAmount": 114,
                  "lines": []
                }""".formatted(taxNumber);
    }

    static String zatcaStandard(String invoiceNumber, String taxNumber, String env, String status) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-OBS-STD",
                  "invoiceNumber": "%s",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "10000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "14:30:00",
                  "seller": {"partyId":"s","vatNumber":"300000000100003","buildingNumber":"1","postalCode":"12345","city":"Riyadh","countryCode":"SA"},
                  "buyer":  {"partyId":"b","vatNumber":"300000000200003","buildingNumber":"2","postalCode":"54321","city":"Riyadh","countryCode":"SA"},
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "payableAmount": 575.00,
                  "clearanceStatus": "CLEARED",
                  "lines": [
                    {
                      "lineNumber": 1, "itemCode": "I", "description": "X",
                      "unitType": "EA", "quantity": 1.0, "unitPrice": 500.00,
                      "lineExtensionAmount": 500.00, "netAmount": 500.00,
                      "vatCategoryCode": "S", "vatRate": 15.00, "vatAmount": 75.00
                    }
                  ]
                }""".formatted(taxNumber, env, status, invoiceNumber);
    }

    static String zatcaStandardBadVat(String taxNumber) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "SANDBOX",
                  "status": "VALID",
                  "invoiceNumber": "OBS-STD-BADVAT",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "10000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "14:30:00",
                  "seller": {"partyId":"s","vatNumber":"BADVAT","buildingNumber":"1","postalCode":"12345","city":"Riyadh","countryCode":"SA"},
                  "buyer":  {"partyId":"b","vatNumber":"300000000200003","buildingNumber":"2","postalCode":"54321","city":"Riyadh","countryCode":"SA"},
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "payableAmount": 575.00,
                  "lines": [
                    {"lineNumber": 1, "itemCode": "I", "description": "X", "unitType":"EA",
                     "quantity": 1.0, "unitPrice": 500.00, "lineExtensionAmount": 500.00,
                     "netAmount": 500.00, "vatCategoryCode": "S", "vatRate": 15.00, "vatAmount": 75.00}
                  ]
                }""".formatted(taxNumber);
    }

    static String zatcaSimplified(String invoiceNumber, String taxNumber, String env, String status) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "%s",
                  "status": "%s",
                  "erpReferenceId": "ERP-OBS-SIMP",
                  "invoiceNumber": "%s",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "00000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "15:00:00",
                  "seller": {"partyId":"s","vatNumber":"300000000100003","buildingNumber":"1","postalCode":"12345","city":"Riyadh","countryCode":"SA"},
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "payableAmount": 575.00,
                  "reportingStatus": "REPORTED",
                  "lines": [
                    {"lineNumber": 1, "itemCode": "I", "description": "X", "unitType":"EA",
                     "quantity": 1.0, "unitPrice": 500.00, "lineExtensionAmount": 500.00,
                     "netAmount": 500.00, "vatCategoryCode": "S", "vatRate": 15.00, "vatAmount": 75.00}
                  ]
                }""".formatted(taxNumber, env, status, invoiceNumber);
    }

    static String zatcaSimplifiedBadTtc(String taxNumber) {
        return """
                {
                  "companyRegistrationNumber": "%s",
                  "environment": "SANDBOX",
                  "status": "VALID",
                  "invoiceNumber": "OBS-SIMP-BADTTC",
                  "invoiceTypeCode": "388",
                  "transactionTypeCode": "10000000",
                  "issueDate": "2026-05-27",
                  "issueTime": "15:00:00",
                  "seller": {"partyId":"s","vatNumber":"300000000100003","buildingNumber":"1","postalCode":"12345","city":"Riyadh","countryCode":"SA"},
                  "currency": "SAR",
                  "lineExtensionAmount": 500.00,
                  "taxExclusiveAmount": 500.00,
                  "taxAmount": 75.00,
                  "taxInclusiveAmount": 575.00,
                  "payableAmount": 575.00,
                  "lines": [
                    {"lineNumber": 1, "itemCode": "I", "description": "X", "unitType":"EA",
                     "quantity": 1.0, "unitPrice": 500.00, "lineExtensionAmount": 500.00,
                     "netAmount": 500.00, "vatCategoryCode": "S", "vatRate": 15.00, "vatAmount": 75.00}
                  ]
                }""".formatted(taxNumber);
    }
}
