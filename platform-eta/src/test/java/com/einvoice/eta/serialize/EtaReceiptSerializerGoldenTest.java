package com.einvoice.eta.serialize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.einvoice.core.authority.SerializedPayload;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.EtaReceiptLine;
import com.einvoice.core.domain.eta.EtaReceiptLineTax;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EtaReceiptSerializerGoldenTest {

    private EtaReceiptSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new EtaReceiptSerializer();
    }

    static Stream<Arguments> documentTypes() {
        return Stream.of(
                Arguments.of("r", EtaReceiptDocumentType.r, false),
                Arguments.of("cr", EtaReceiptDocumentType.cr, true),
                Arguments.of("rr", EtaReceiptDocumentType.rr, true),
                Arguments.of("r-with-discounts", EtaReceiptDocumentType.r,
                        false));
    }

    @ParameterizedTest(name = "receipt type {0}")
    @MethodSource("documentTypes")
    void serializerOutputMatchesGoldenFile(String suffix,
            EtaReceiptDocumentType docType, boolean requiresOriginal)
            throws IOException {
        EtaReceiptHeader header = buildTestHeader(docType);
        if (requiresOriginal) {
            header.setOriginalReceiptId(
                    UUID.fromString("00000000-0000-0000-0000-000000000099"));
        }
        if (docType == EtaReceiptDocumentType.cr) {
            header.setBuyerData(buyerMap());
        }
        EtaReceiptLine line = buildTestLine(docType == EtaReceiptDocumentType.rr
                ? "Test Receipt Item Return" : "Test Receipt Item");
        if ("r-with-discounts".equals(suffix)) {
            ArrayNode commercialDiscounts = JsonNodeFactory.instance
                    .arrayNode();
            commercialDiscounts.add(JsonNodeFactory.instance.objectNode()
                    .put("discountRate", new BigDecimal("10.00"))
                    .put("discountAmount", new BigDecimal("5.00")));
            line.setCommercialDiscountData(commercialDiscounts);
            ArrayNode itemDiscounts = JsonNodeFactory.instance.arrayNode();
            itemDiscounts.add(JsonNodeFactory.instance.objectNode()
                    .put("itemsDiscount", new BigDecimal("3.00")));
            line.setItemDiscountData(itemDiscounts);
            header.setId(UUID.fromString(
                    "00000000-0000-0000-0000-000000000005"));
            header.setIssueDatetime(OffsetDateTime.parse(
                    "2026-05-13T17:00:00+02:00"));
            header.setTotalCommercialDiscount(
                    new BigDecimal("5.00000"));
            header.setTotalItemsDiscountAmount(
                    new BigDecimal("3.00000"));
            header.setNetAmount(new BigDecimal("42.00000"));
            header.setTotalAmount(new BigDecimal("49.00000"));
        }
        header.setLines(List.of(line));

        SerializedPayload result = serializer.serialize(header);
        assertNotNull(result);
        assertNotNull(result.canonicalBytes());

        String goldenPath = "/golden/receipts/" + suffix + ".json";
        byte[] canonical = result.canonicalBytes();
        if ("1".equals(System.getenv("UPDATE_GOLDEN"))) {
            Path moduleLocal = Path.of(
                    "src/test/resources/golden/receipts/" + suffix + ".json");
            Path target = Files.isDirectory(Path.of("src/test/resources"))
                    ? moduleLocal
                    : Path.of("platform-eta/src/test/resources/golden/receipts/"
                            + suffix + ".json");
            Files.createDirectories(target.getParent());
            Files.write(target, normalize(canonical));
            return;
        }
        InputStream is = getClass().getResourceAsStream(goldenPath);
        if (is == null) {
            throw new IOException("Golden file not found: " + goldenPath);
        }
        byte[] expected = is.readAllBytes();

        assertArrayEquals(
                normalize(expected),
                normalize(canonical),
                "Serialized output does not match golden file for type "
                        + suffix);
    }

    private byte[] normalize(byte[] json) {
        return new String(json, StandardCharsets.UTF_8)
                .trim().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> sellerMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("branchNumber", "0");
        m.put("companyName", "Test Company");
        m.put("country", "EG");
        m.put("governate", "Cairo");
        m.put("regionCity", "Nasr City");
        m.put("street", "Test Street");
        m.put("buildingNumber", "1");
        m.put("postalCode", "12345");
        m.put("type", "B");
        m.put("id", "123456789");
        m.put("name", "Test Company");
        return m;
    }

    private static Map<String, Object> buyerMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "P");
        m.put("id", "987654321");
        m.put("name", "Test Buyer");
        m.put("country", "EG");
        m.put("governate", "Cairo");
        m.put("regionCity", "Nasr City");
        m.put("street", "Test Street");
        m.put("buildingNumber", "2");
        m.put("postalCode", "12345");
        return m;
    }

    private EtaReceiptHeader buildTestHeader(
            EtaReceiptDocumentType docType) {
        return EtaReceiptHeader.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-00000000000"
                        + (docType == EtaReceiptDocumentType.r ? "2"
                                : docType == EtaReceiptDocumentType.cr ? "3" : "4")))
                .companyId(UUID.fromString(
                        "00000000-0000-0000-0000-000000000010"))
                .authorityEnvironmentId((short) 2)
                .receiptNumber("REC-001")
                .documentType(docType)
                .documentTypeVersion("1.2")
                .issueDatetime(OffsetDateTime.parse(
                        docType == EtaReceiptDocumentType.r
                                ? "2026-05-13T14:30:00+02:00"
                                : docType == EtaReceiptDocumentType.cr
                                        ? "2026-05-13T15:00:00+02:00"
                                        : "2026-05-13T16:00:00+02:00"))
                .sellerData(sellerMap())
                .posSerial("POS-001")
                .paymentMethod("CASH")
                .currency("EGP")
                .totalSalesAmount(new BigDecimal("50.00000"))
                .totalCommercialDiscount(BigDecimal.ZERO)
                .extraDiscountAmount(BigDecimal.ZERO)
                .totalItemsDiscountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("50.00000"))
                .totalAmount(new BigDecimal("57.00000"))
                .state(DocumentState.DRAFT)
                .build();
    }

    private EtaReceiptLine buildTestLine(String description) {
        EtaReceiptLineTax tax = EtaReceiptLineTax.builder()
                .taxType("T1")
                .taxRate(new BigDecimal("14.00000"))
                .taxAmount(new BigDecimal("7.00000"))
                .build();
        return EtaReceiptLine.builder()
                .lineNumber(1)
                .internalCode("ITEM002")
                .itemType("EGS")
                .itemCode("EGS-001")
                .description(description)
                .unitType("EA")
                .quantity(new BigDecimal("2.00000"))
                .unitPrice(new BigDecimal("25.00000"))
                .salesTotal(new BigDecimal("50.00000"))
                .valueDifference(BigDecimal.ZERO)
                .totalTaxableFees(BigDecimal.ZERO)
                .netTotal(new BigDecimal("50.00000"))
                .taxAmount(new BigDecimal("7.00000"))
                .total(new BigDecimal("57.00000"))
                .taxes(List.of(tax))
                .build();
    }
}
