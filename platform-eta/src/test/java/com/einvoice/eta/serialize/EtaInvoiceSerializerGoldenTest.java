package com.einvoice.eta.serialize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.einvoice.core.authority.SerializedPayload;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaInvoiceLine;
import com.einvoice.core.domain.eta.EtaInvoiceLineTax;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.money.EtaMoneyMath;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EtaInvoiceSerializerGoldenTest {

    private EtaInvoiceSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new EtaInvoiceSerializer();
    }

    static Stream<Arguments> documentTypes() {
        return Stream.of(
                Arguments.of("i", EtaInvoiceDocumentType.i),
                Arguments.of("c", EtaInvoiceDocumentType.c),
                Arguments.of("d", EtaInvoiceDocumentType.d),
                Arguments.of("ei", EtaInvoiceDocumentType.ei),
                Arguments.of("ec", EtaInvoiceDocumentType.ec),
                Arguments.of("ed", EtaInvoiceDocumentType.ed));
    }

    @ParameterizedTest(name = "document type {0}")
    @MethodSource("documentTypes")
    void serializerOutputMatchesGoldenFile(String suffix, EtaInvoiceDocumentType docType)
            throws IOException {
        EtaInvoiceHeader header = buildTestHeader(docType);
        if (docType.requiresOriginalDocument()) {
            header.setOriginalDocumentId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        }
        EtaInvoiceLine line = buildTestLine();
        header.setLines(List.of(line));

        SerializedPayload result = serializer.serialize(header);
        assertNotNull(result);
        assertNotNull(result.canonicalBytes());

        Path goldenPath = Path.of("src/test/resources/golden/invoices/" + suffix + ".json");
        byte[] expected = Files.readAllBytes(goldenPath);

        assertArrayEquals(
                normalize(expected),
                normalize(result.canonicalBytes()),
                "Serialized output does not match golden file for type " + suffix);
    }

    private byte[] normalize(byte[] json) {
        return new String(json, StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8);
    }

    private EtaInvoiceHeader buildTestHeader(EtaInvoiceDocumentType docType) {
        return EtaInvoiceHeader.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .companyId(UUID.fromString("00000000-0000-0000-0000-000000000010"))
                .authorityEnvironmentId((short) 2)
                .invoiceNumber("INV-001")
                .documentType(docType)
                .issueDatetime(OffsetDateTime.parse("2026-05-13T10:00:00+02:00"))
                .sellerData(Map.of(
                        "type", "B", "id", "123456789", "name", "Test Company",
                        "country", "EG", "governate", "Cairo"))
                .buyerData(Map.of(
                        "type", "B", "id", "987654321", "name", "Test Buyer",
                        "country", "EG", "governate", "Cairo"))
                .taxpayerActivityCode("4610")
                .currency("EGP")
                .totalSalesAmount(new BigDecimal("100.00000"))
                .totalDiscountAmount(BigDecimal.ZERO)
                .extraDiscountAmount(BigDecimal.ZERO)
                .totalItemsDiscountAmount(BigDecimal.ZERO)
                .netAmount(new BigDecimal("100.00000"))
                .totalAmount(new BigDecimal("114.00000"))
                .state(EtaInvoiceState.DRAFT)
                .build();
    }

    private EtaInvoiceLine buildTestLine() {
        EtaInvoiceLineTax tax = EtaInvoiceLineTax.builder()
                .taxType("T1")
                .taxRate(new BigDecimal("14.00000"))
                .taxAmount(new BigDecimal("14.00000"))
                .build();
        return EtaInvoiceLine.builder()
                .lineNumber(1)
                .internalCode("ITEM001")
                .itemType("GS1")
                .itemCode("1234567890123")
                .description("Test Item")
                .unitType("EA")
                .quantity(BigDecimal.ONE)
                .unitValue(Map.of(
                        "currencySold", "EGP",
                        "amountEGP", "100.00000",
                        "amountSold", "100.00000",
                        "currencyExchangeRate", "1.00000"))
                .salesTotal(new BigDecimal("100.00000"))
                .discountAmount(BigDecimal.ZERO)
                .itemsDiscount(BigDecimal.ZERO)
                .valueDifference(BigDecimal.ZERO)
                .totalTaxableFees(BigDecimal.ZERO)
                .netTotal(new BigDecimal("100.00000"))
                .taxAmount(new BigDecimal("14.00000"))
                .total(new BigDecimal("114.00000"))
                .taxes(List.of(tax))
                .build();
    }
}
