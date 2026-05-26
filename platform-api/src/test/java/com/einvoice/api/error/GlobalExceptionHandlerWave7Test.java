package com.einvoice.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.einvoice.core.error.AppendOnlyViolationException;
import com.einvoice.core.error.BulkBatchLimitExceededException;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateInvoiceNumberException;
import com.einvoice.core.error.DuplicateReceiptNumberException;
import com.einvoice.core.error.IncompatibleOriginalDocumentException;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.NoCertificateConfiguredException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.TotalsInconsistentException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerWave7Test {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void duplicateInvoiceNumber_returns409() {
        var ex = new DuplicateInvoiceNumberException("dup", UUID.randomUUID(), "INV-001");
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateInvoiceNumber(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_INVOICE_NUMBER", response.getBody().code());
    }

    @Test
    void duplicateReceiptNumber_returns409() {
        var ex = new DuplicateReceiptNumberException("dup", UUID.randomUUID(), "REC-001");
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateReceiptNumber(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_RECEIPT_NUMBER", response.getBody().code());
    }

    @Test
    void missingOriginalDocument_returns400() {
        var ex = new MissingOriginalDocumentException("missing", UUID.randomUUID(), "c");
        ResponseEntity<ErrorResponse> response = handler.handleMissingOriginalDocument(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_ORIGINAL_DOCUMENT", response.getBody().code());
    }

    @Test
    void incompatibleOriginalDocument_returns400() {
        var ex = new IncompatibleOriginalDocumentException("incompatible", "i", "r");
        ResponseEntity<ErrorResponse> response = handler.handleIncompatibleOriginalDocument(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INCOMPATIBLE_ORIGINAL_DOCUMENT", response.getBody().code());
    }

    @Test
    void totalsInconsistent_returns400() {
        var ex = new TotalsInconsistentException("totals", "netAmount", "100", "90");
        ResponseEntity<ErrorResponse> response = handler.handleTotalsInconsistent(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("TOTALS_INCONSISTENT", response.getBody().code());
        assertNotNull(response.getBody().details());
    }

    @Test
    void noCertificateConfigured_returns409() {
        var ex = new NoCertificateConfiguredException("no cert", UUID.randomUUID(), (short) 2);
        ResponseEntity<ErrorResponse> response = handler.handleNoCertificateConfigured(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("NO_CERTIFICATE_CONFIGURED", response.getBody().code());
    }

    @Test
    void documentNotDraft_returns409() {
        var ex = new DocumentNotDraftException("not draft", "SUBMITTED");
        ResponseEntity<ErrorResponse> response = handler.handleDocumentNotDraft(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DOCUMENT_NOT_DRAFT", response.getBody().code());
    }

    @Test
    void invalidLifecycleTransition_returns409() {
        var ex = new InvalidLifecycleTransitionException("invalid", "CANCELLED", "EDIT");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidLifecycleTransition(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INVALID_LIFECYCLE_TRANSITION", response.getBody().code());
    }

    @Test
    void optimisticLockConflict_returns409_withStructuredBody() {
        Object current = Map.of("id", "abc", "version", 2);
        var ex = new OptimisticLockConflictException("conflict", 0, 2, current);
        ResponseEntity<ConflictResponse> response = handler.handleOptimisticLockConflict(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ConflictResponse body = response.getBody();
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", body.code());
        assertEquals(0, body.expectedVersion());
        assertEquals(2, body.actualVersion());
        assertNotNull(body.current());
    }

    @Test
    void appendOnlyViolation_returns500() {
        var ex = new AppendOnlyViolationException("violation", "invoice_artifacts");
        ResponseEntity<ErrorResponse> response = handler.handleAppendOnlyViolation(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("APPEND_ONLY_VIOLATION", response.getBody().code());
    }

    @Test
    void bulkBatchLimitExceeded_returns400() {
        var ex = new BulkBatchLimitExceededException("too many", 250, 200);
        ResponseEntity<ErrorResponse> response = handler.handleBulkBatchLimitExceeded(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BULK_BATCH_LIMIT_EXCEEDED", response.getBody().code());
        assertNotNull(response.getBody().details());
    }

    @Test
    void jpaOptimisticLock_isMappedToConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleJpaOptimisticLock(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        "EtaInvoiceHeader", UUID.randomUUID()));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().code().contains("OPTIMISTIC_LOCK"));
    }
}
