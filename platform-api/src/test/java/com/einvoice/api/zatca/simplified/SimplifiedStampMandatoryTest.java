package com.einvoice.api.zatca.simplified;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.einvoice.api.zatca.submission.service.SimplifiedStampGuard;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.error.MissingCryptographicStampException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimplifiedStampMandatoryTest {

    @Test
    void rejects_submitted_without_cryptographic_stamp() {
        var h = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 1)
                .invoiceNumber("SIM-001")
                .transactionTypeCode("0200000")
                .issueDate(LocalDate.now())
                .issueTime(LocalTime.NOON)
                .sellerData(Map.of())
                .status(DocumentState.SUBMITTED)
                .build();
        assertThatThrownBy(() -> SimplifiedStampGuard.assertPresent(h))
                .isInstanceOf(MissingCryptographicStampException.class)
                .hasMessageContaining("BR-KSA-60");
    }

    @Test
    void rejects_accepted_with_stamp_but_no_artifact() {
        var h = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .status(DocumentState.ACCEPTED)
                .cryptographicStampValue("MEUCIQ...")
                .build();
        assertThatThrownBy(() -> SimplifiedStampGuard.assertPresent(h))
                .isInstanceOf(MissingCryptographicStampException.class);
    }

    @Test
    void passes_when_both_present_on_submitted() {
        var h = ZatcaSimplifiedHeader.builder()
                .id(UUID.randomUUID())
                .status(DocumentState.SUBMITTED)
                .cryptographicStampValue("MEUCIQ...")
                .signedXmlArtifactId(UUID.randomUUID())
                .build();
        assertThatCode(() -> SimplifiedStampGuard.assertPresent(h))
                .doesNotThrowAnyException();
    }

    @Test
    void passes_when_status_is_rejected_or_draft() {
        for (DocumentState s : List.of(
                DocumentState.DRAFT, DocumentState.REJECTED,
                DocumentState.CANCELLED)) {
            var h = ZatcaSimplifiedHeader.builder()
                    .id(UUID.randomUUID())
                    .status(s)
                    .build();
            assertThatCode(() -> SimplifiedStampGuard.assertPresent(h))
                    .as("status %s should not require stamp", s)
                    .doesNotThrowAnyException();
        }
    }
}
