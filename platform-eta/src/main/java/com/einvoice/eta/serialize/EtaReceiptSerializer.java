package com.einvoice.eta.serialize;

import com.einvoice.core.authority.SerializedPayload;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.EtaReceiptLine;
import com.einvoice.core.domain.eta.EtaReceiptLineTax;
import com.einvoice.core.money.EtaMoneyMath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Serializes ETA receipt headers into the JSON format required by the authority. */
@Component
public class EtaReceiptSerializer {

    private final ObjectMapper etaObjectMapper;

    /** Constructs an EtaReceiptSerializer with a configured ObjectMapper. */
    public EtaReceiptSerializer() {
        this.etaObjectMapper = new ObjectMapper();
        this.etaObjectMapper.registerModule(new JavaTimeModule());
        this.etaObjectMapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.etaObjectMapper.disable(
                SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.etaObjectMapper.enable(
                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.etaObjectMapper.setSerializationInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
    }

    /**
     * Serializes the given receipt header into a JSON payload.
     *
     * @param header the receipt header to serialize
     * @return the serialized JSON payload
     */
    public SerializedPayload serialize(EtaReceiptHeader header) {
        validateRequiredFields(header);

        ObjectNode root = etaObjectMapper.createObjectNode();
        root.put("documentType",
                header.getDocumentType().name());
        root.put("documentTypeVersion",
                header.getDocumentTypeVersion());
        root.put("dateTimeIssued",
                header.getIssueDatetime().toString());
        root.put("internalID", header.getReceiptNumber());
        root.put("posSerial", header.getPosSerial());
        root.put("paymentMethod", header.getPaymentMethod());

        if (header.getOriginalReceiptId() != null) {
            root.put("originalReceiptId",
                    header.getOriginalReceiptId().toString());
        }

        if (header.getSellerData() != null) {
            root.set("seller",
                    etaObjectMapper.valueToTree(header.getSellerData()));
        }
        if (header.getBuyerData() != null) {
            root.set("buyer",
                    etaObjectMapper.valueToTree(header.getBuyerData()));
        }

        ObjectNode totals = root.putObject("documentTotals");
        totals.put("totalSalesAmount",
                formatMoney(header.getTotalSalesAmount()));
        totals.put("totalDiscountAmount",
                formatMoney(header.getTotalDiscountAmount()));
        totals.put("extraDiscountAmount",
                formatMoney(header.getExtraDiscountAmount()));
        totals.put("totalItemsDiscountAmount",
                formatMoney(header.getTotalItemsDiscountAmount()));
        totals.put("netAmount", formatMoney(header.getNetAmount()));
        totals.put("totalAmount", formatMoney(header.getTotalAmount()));

        ArrayNode linesNode = root.putArray("receiptLines");
        for (EtaReceiptLine line : header.getLines()) {
            ObjectNode lineNode = linesNode.addObject();
            lineNode.put("internalCode", line.getInternalCode());
            lineNode.put("itemType", line.getItemType());
            lineNode.put("itemCode", line.getItemCode());
            lineNode.put("description", line.getDescription());
            lineNode.put("unitType", line.getUnitType());
            lineNode.put("quantity", formatMoney(line.getQuantity()));

            if (line.getUnitValue() != null) {
                lineNode.set("unitValue",
                        etaObjectMapper.valueToTree(line.getUnitValue()));
            }

            lineNode.put("salesTotal",
                    formatMoney(line.getSalesTotal()));
            lineNode.put("discount",
                    formatMoney(line.getDiscountAmount()));
            if (line.getDiscountRate() != null) {
                lineNode.put("discountRate",
                        formatMoney(line.getDiscountRate()));
            }
            lineNode.put("itemsDiscount",
                    formatMoney(line.getItemsDiscount()));
            lineNode.put("valueDifference",
                    formatMoney(line.getValueDifference()));
            lineNode.put("totalTaxableFees",
                    formatMoney(line.getTotalTaxableFees()));
            lineNode.put("netTotal", formatMoney(line.getNetTotal()));
            lineNode.put("total", formatMoney(line.getTotal()));

            if (line.getItemId() != null) {
                lineNode.put("itemId", line.getItemId().toString());
            }

            ArrayNode taxesNode = lineNode.putArray("taxableItems");
            for (EtaReceiptLineTax tax : line.getTaxes()) {
                ObjectNode taxNode = taxesNode.addObject();
                taxNode.put("taxType", tax.getTaxType());
                if (tax.getSubType() != null) {
                    taxNode.put("subType", tax.getSubType());
                }
                if (tax.getTaxRate() != null) {
                    taxNode.put("rate", formatMoney(tax.getTaxRate()));
                }
                taxNode.put("amount", formatMoney(tax.getTaxAmount()));
            }
        }

        try {
            byte[] bytes = etaObjectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(root);
            return new SerializedPayload(bytes, "application/json");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize receipt", e);
        }
    }

    private void validateRequiredFields(EtaReceiptHeader header) {
        if (header.getSellerData() == null) {
            throw new IllegalArgumentException(
                    "sellerData is required for all receipt types");
        }
        if (header.getDocumentType().requiresOriginalDocument()
                && header.getOriginalReceiptId() == null) {
            throw new IllegalArgumentException(
                    "originalReceiptId is required for receipt type "
                            + header.getDocumentType().name());
        }
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return EtaMoneyMath.round5(BigDecimal.ZERO).toPlainString();
        }
        return EtaMoneyMath.round5(value).toPlainString();
    }
}
