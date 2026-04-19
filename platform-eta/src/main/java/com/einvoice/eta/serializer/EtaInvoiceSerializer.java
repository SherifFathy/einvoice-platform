package com.einvoice.eta.serializer;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Serializes invoices to the ETA JSON document format.
 */
@Service
public class EtaInvoiceSerializer {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final ObjectMapper objectMapper;

    /**
     * Creates a new EtaInvoiceSerializer.
     *
     * @param objectMapper the JSON object mapper
     */
    public EtaInvoiceSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes an invoice to the ETA JSON format.
     *
     * @param invoice the invoice to serialize
     * @return the JSON string representation
     */
    public String serialize(Invoice invoice) {
        ObjectNode doc = objectMapper.createObjectNode();

        doc.put("documentTypeCode", resolveDocumentTypeCode(invoice));
        doc.put("documentTypeVersion", resolveDocumentTypeVersion(invoice));
        doc.put("dateTimeIssued", formatDateTime(invoice));
        doc.put("taxpayerActivityCode", resolveActivityCode(invoice));
        doc.put("internalID", invoice.getInvoiceNumber());

        ObjectNode taxpayer = buildTaxpayer(invoice);
        doc.set("taxpayer", taxpayer);

        ObjectNode receiver = buildReceiver(invoice);
        if (receiver != null) {
            doc.set("receiver", receiver);
        }

        ObjectNode document = buildDocumentBody(invoice);
        doc.set("document", document);

        ArrayNode documents = objectMapper.createArrayNode();
        documents.add(doc);
        ObjectNode root = objectMapper.createObjectNode();
        root.set("documents", documents);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new EtaSerializationException("Failed to serialize invoice to ETA JSON", e);
        }
    }

    private String resolveDocumentTypeCode(Invoice invoice) {
        return switch (invoice.getType()) {
          case TAX_INVOICE -> "i";
          case SIMPLIFIED_TAX_INVOICE -> "s";
          case CREDIT_NOTE -> "c";
          case DEBIT_NOTE -> "d";
        };
    }

    private String resolveDocumentTypeVersion(Invoice invoice) {
        return "1.0";
    }

    private String formatDateTime(Invoice invoice) {
        if (invoice.getCreatedAt() != null) {
            return invoice.getCreatedAt().format(DATETIME_FORMATTER);
        }
        return invoice.getIssueDate().atStartOfDay().format(DATETIME_FORMATTER);
    }

    private String resolveActivityCode(Invoice invoice) {
        if (invoice.getSellerData() != null && !invoice.getSellerData().isBlank()) {
            try {
                JsonNode sellerNode = objectMapper.readTree(invoice.getSellerData());
                String code = sellerNode.path("activityCode").asText(null);
                if (code != null) {
                    return code;
                }
            } catch (Exception ignored) {
            }
        }
        return "4610";
    }

    private ObjectNode buildTaxpayer(Invoice invoice) {
        ObjectNode taxpayer = objectMapper.createObjectNode();

        if (invoice.getCompany() != null) {
            taxpayer.put("name", invoice.getCompany().getNameEn());
            taxpayer.put("type", "B");
            String vatNumber = invoice.getCompany().getVatNumber();
            if (vatNumber != null) {
                ObjectNode idNode = objectMapper.createObjectNode();
                idNode.put("id", vatNumber);
                idNode.put("schemeID", "VAT");
                taxpayer.set("id", idNode);
            }

            ObjectNode address = objectMapper.createObjectNode();
            address.put("country", "EG");
            if (invoice.getSellerData() != null && !invoice.getSellerData().isBlank()) {
                try {
                    JsonNode sellerNode = objectMapper.readTree(invoice.getSellerData());
                    putIfPresent(address, sellerNode, "governate", "governate");
                    putIfPresent(address, sellerNode, "regionCity", "regionCity");
                    putIfPresent(address, sellerNode, "street", "street");
                    putIfPresent(address, sellerNode, "buildingNumber", "buildingNumber");
                } catch (Exception ignored) {
                }
            }
            taxpayer.set("address", address);
        }

        return taxpayer;
    }

    private ObjectNode buildReceiver(Invoice invoice) {
        if (invoice.getBuyer() == null && (invoice.getBuyerData() == null
                || invoice.getBuyerData().isBlank())) {
            return null;
        }

        ObjectNode receiver = objectMapper.createObjectNode();

        if (invoice.getBuyer() != null) {
            receiver.put("name", invoice.getBuyer().getNameEn());
            receiver.put("type", "B");
            String buyerVat = invoice.getBuyer().getVatNumber();
            if (buyerVat != null && !buyerVat.isBlank()) {
                ObjectNode idNode = objectMapper.createObjectNode();
                idNode.put("id", buyerVat);
                idNode.put("schemeID", "VAT");
                receiver.set("id", idNode);
            }
        } else if (invoice.getBuyerData() != null && !invoice.getBuyerData().isBlank()) {
            try {
                JsonNode buyerNode = objectMapper.readTree(invoice.getBuyerData());
                String name = buyerNode.path("name").asText("");
                if (!name.isBlank()) {
                    receiver.put("name", name);
                }
                receiver.put("type", "P");
            } catch (Exception ignored) {
            }
        }

        ObjectNode address = objectMapper.createObjectNode();
        address.put("country", "EG");
        receiver.set("address", address);

        return receiver;
    }

    private ObjectNode buildDocumentBody(Invoice invoice) {
        ObjectNode document = objectMapper.createObjectNode();

        document.put("invoiceNumber", invoice.getInvoiceNumber());
        document.put("invoiceDate", invoice.getIssueDate().format(DATE_FORMATTER));

        if (invoice.getBuyer() != null) {
            String buyerVat = invoice.getBuyer().getVatNumber();
            if (buyerVat != null) {
                document.put("receiverId", buyerVat);
            }
        }

        if (invoice.getPaymentMeansCode() != null) {
            document.put("payment", invoice.getPaymentMeansCode());
        }

        ArrayNode invoiceLines = buildInvoiceLines(invoice.getLines());
        document.set("invoiceLines", invoiceLines);

        ObjectNode totals = buildTotals(invoice);
        document.set("totalSalesAmount", totals.get("totalSalesAmount"));
        document.set("totalDiscountAmount", totals.get("totalDiscountAmount"));
        document.set("netAmount", totals.get("netAmount"));
        document.set("totalItemsDiscountAmount",
                totals.get("totalItemsDiscountAmount"));

        ArrayNode taxTotals = buildTaxTotals(invoice);
        document.set("taxTotals", taxTotals);

        document.put("totalAmount", invoice.getTotalWithVat());

        return document;
    }

    private ArrayNode buildInvoiceLines(List<InvoiceLine> lines) {
        ArrayNode linesArray = objectMapper.createArrayNode();
        if (lines == null) {
            return linesArray;
        }
        for (int i = 0; i < lines.size(); i++) {
            InvoiceLine line = lines.get(i);
            ObjectNode lineNode = objectMapper.createObjectNode();
            lineNode.put("number", i + 1);

            String description = line.getDescriptionEn();
            if (description != null) {
                lineNode.put("description", description);
            } else {
                lineNode.put("description", "Item " + (i + 1));
            }

            if (line.getItem() != null) {
                lineNode.put("itemCode", line.getItem().getCode());
            }

            lineNode.put("unitType",
                    line.getUnit() != null ? line.getUnit() : "EA");
            lineNode.put("quantity", line.getQuantity());
            lineNode.put("unitValue", line.getUnitPrice());

            ObjectNode discount = objectMapper.createObjectNode();
            BigDecimal discountAmount = line.getDiscountAmount() != null
                    ? line.getDiscountAmount() : BigDecimal.ZERO;
            discount.put("amount", discountAmount);
            lineNode.set("discount", discount);

            BigDecimal lineNet = line.getLineNetAmount() != null
                    ? line.getLineNetAmount() : BigDecimal.ZERO;
            lineNode.put("netTotal", lineNet.setScale(2, RoundingMode.HALF_UP));

            BigDecimal vatRate = line.getVatRate() != null
                    ? line.getVatRate() : BigDecimal.ZERO;
            BigDecimal vatAmount = line.getLineVatAmount() != null
                    ? line.getLineVatAmount() : BigDecimal.ZERO;

            ObjectNode taxableItem = objectMapper.createObjectNode();
            taxableItem.put("taxType", "VAT");
            taxableItem.put("amount", vatAmount.setScale(2, RoundingMode.HALF_UP));
            taxableItem.put("rate", vatRate);
            ArrayNode taxableItems = objectMapper.createArrayNode();
            taxableItems.add(taxableItem);
            lineNode.set("taxableItems", taxableItems);

            BigDecimal total = lineNet.add(vatAmount).setScale(2, RoundingMode.HALF_UP);
            lineNode.put("total", total);

            linesArray.add(lineNode);
        }
        return linesArray;
    }

    private ObjectNode buildTotals(Invoice invoice) {
        ObjectNode totals = objectMapper.createObjectNode();
        BigDecimal totalSales = invoice.getTotalLineNet() != null
                ? invoice.getTotalLineNet() : BigDecimal.ZERO;
        BigDecimal totalDiscount = invoice.getTotalAllowances() != null
                ? invoice.getTotalAllowances() : BigDecimal.ZERO;
        BigDecimal netAmount = invoice.getTotalWithoutVat() != null
                ? invoice.getTotalWithoutVat() : BigDecimal.ZERO;

        totals.put("totalSalesAmount", totalSales.setScale(2, RoundingMode.HALF_UP));
        totals.put("totalDiscountAmount", totalDiscount.setScale(2, RoundingMode.HALF_UP));
        totals.put("netAmount", netAmount.setScale(2, RoundingMode.HALF_UP));
        totals.put("totalItemsDiscountAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return totals;
    }

    private ArrayNode buildTaxTotals(Invoice invoice) {
        ArrayNode taxTotals = objectMapper.createArrayNode();
        if (invoice.getTotalVat() != null) {
            ObjectNode vatTotal = objectMapper.createObjectNode();
            vatTotal.put("taxType", "VAT");
            vatTotal.put("amount", invoice.getTotalVat().setScale(2, RoundingMode.HALF_UP));
            taxTotals.add(vatTotal);
        }
        return taxTotals;
    }

    private void putIfPresent(ObjectNode target, JsonNode source,
            String targetField, String sourceField) {
        String value = source.path(sourceField).asText(null);
        if (value != null && !value.isBlank()) {
            target.put(targetField, value);
        }
    }

    /**
     * Exception thrown when ETA serialization fails.
     */
    public static class EtaSerializationException extends RuntimeException {
        public EtaSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
