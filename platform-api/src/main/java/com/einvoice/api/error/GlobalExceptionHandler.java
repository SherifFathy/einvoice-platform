package com.einvoice.api.error;

import com.einvoice.core.error.AppendOnlyViolationException;
import com.einvoice.core.error.AssignmentExistsException;
import com.einvoice.core.error.AuthorityEnvironmentNotFoundException;
import com.einvoice.core.error.BranchCodeDuplicateException;
import com.einvoice.core.error.BranchIdNotAllowedException;
import com.einvoice.core.error.BulkBatchLimitExceededException;
import com.einvoice.core.error.BuyerIdentityRequiredException;
import com.einvoice.core.error.ChainBusyException;
import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.core.error.CompanyNotFoundException;
import com.einvoice.core.error.ConfigNotFoundException;
import com.einvoice.core.error.CustomerNotFoundException;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateInternalCodeException;
import com.einvoice.core.error.DuplicateInvoiceNumberException;
import com.einvoice.core.error.DuplicateReceiptNumberException;
import com.einvoice.core.error.DuplicateSimplifiedNumberException;
import com.einvoice.core.error.DuplicateStandardNumberException;
import com.einvoice.core.error.DuplicateTaxNumberException;
import com.einvoice.core.error.DuplicateVatNumberException;
import com.einvoice.core.error.EmailAlreadyExistsException;
import com.einvoice.core.error.InactiveCompanyException;
import com.einvoice.core.error.InboundPayloadArchiveException;
import com.einvoice.core.error.IncompatibleOriginalDocumentException;
import com.einvoice.core.error.InvalidAddressDataException;
import com.einvoice.core.error.InvalidAuthorityEnvironmentException;
import com.einvoice.core.error.InvalidAuthorityForRouteException;
import com.einvoice.core.error.InvalidCustomerTypeException;
import com.einvoice.core.error.InvalidEnvironmentForAuthorityException;
import com.einvoice.core.error.InvalidExpiryDateException;
import com.einvoice.core.error.InvalidItemTypeException;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.error.InvalidRoleForAuthorityException;
import com.einvoice.core.error.InvalidSimplifiedTransactionTypeException;
import com.einvoice.core.error.InvalidUnitValueException;
import com.einvoice.core.error.InvalidVatCategoryException;
import com.einvoice.core.error.InvalidVatNumberException;
import com.einvoice.core.error.InvalidVatRateException;
import com.einvoice.core.error.ItemNotFoundException;
import com.einvoice.core.error.LastSuperUserProtectedException;
import com.einvoice.core.error.MissingBuyerForStandardException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.NoCertificateConfiguredException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.ProductionTokenRequiredException;
import com.einvoice.core.error.TaxNumberDuplicateException;
import com.einvoice.core.error.TotalsInconsistentException;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.error.VatExemptionReasonRequiredException;
import com.einvoice.core.error.WrongOriginalClassException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Centralized exception handler for all REST API errors. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(com.einvoice.core.error.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            com.einvoice.core.error.BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidAuthorityEnvironmentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAuthorityEnvironment(
            InvalidAuthorityEnvironmentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(CompanyContextRequiredException.class)
    public ResponseEntity<ErrorResponse> handleCompanyContextRequired(
            CompanyContextRequiredException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedContextException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedContext(
            UnauthorizedContextException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(InactiveCompanyException.class)
    public ResponseEntity<ErrorResponse> handleInactiveCompany(InactiveCompanyException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(TaxNumberDuplicateException.class)
    public ResponseEntity<ErrorResponse> handleTaxNumberDuplicate(
            TaxNumberDuplicateException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidRoleForAuthorityException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRoleForAuthority(
            InvalidRoleForAuthorityException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AssignmentExistsException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentExists(AssignmentExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BranchCodeDuplicateException.class)
    public ResponseEntity<ErrorResponse> handleBranchCodeDuplicate(
            BranchCodeDuplicateException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(LastSuperUserProtectedException.class)
    public ResponseEntity<ErrorResponse> handleLastSuperUserProtected(
            LastSuperUserProtectedException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /**
     * Handles DuplicateTaxNumberException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DuplicateTaxNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTaxNumber(DuplicateTaxNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("conflictingId", ex.getConflictingId(), "field", ex.getField())));
    }

    /**
     * Handles DuplicateVatNumberException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DuplicateVatNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateVatNumber(DuplicateVatNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("conflictingId", ex.getConflictingId(), "field", ex.getField())));
    }

    /**
     * Handles DuplicateInternalCodeException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DuplicateInternalCodeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInternalCode(DuplicateInternalCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("conflictingId", ex.getConflictingId(), "field", ex.getField())));
    }

    @ExceptionHandler(InvalidAuthorityForRouteException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAuthorityForRoute(
            InvalidAuthorityForRouteException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidEnvironmentForAuthorityException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEnvironmentForAuthority(
            InvalidEnvironmentForAuthorityException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BranchIdNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleBranchIdNotAllowed(BranchIdNotAllowedException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    /**
     * Handles InvalidCustomerTypeException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidCustomerTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCustomerType(InvalidCustomerTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", ex.getValue(), "allowed", ex.getAllowed())));
    }

    /**
     * Handles InvalidItemTypeException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidItemTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidItemType(InvalidItemTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", ex.getValue(), "allowed", ex.getAllowed())));
    }

    /**
     * Handles InvalidVatCategoryException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidVatCategoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVatCategory(InvalidVatCategoryException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", ex.getValue(), "allowed", ex.getAllowed())));
    }

    /**
     * Handles InvalidVatRateException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidVatRateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVatRate(InvalidVatRateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", ex.getValue())));
    }

    /**
     * Handles InvalidAddressDataException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidAddressDataException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAddressData(InvalidAddressDataException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("missingKeys", ex.getMissingKeys())));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(CustomerNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleItemNotFound(ItemNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    /**
     * Handles InvalidVatNumberException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidVatNumberException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVatNumber(InvalidVatNumberException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", ex.getValue())));
    }

    /**
     * Handles ProductionTokenRequiredException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(ProductionTokenRequiredException.class)
    public ResponseEntity<ErrorResponse> handleProductionTokenRequired(ProductionTokenRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", String.valueOf(ex.getValue()))));
    }

    /**
     * Handles UnrecognizedPropertyException for unknown JSON fields.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(UnrecognizedPropertyException.class)
    public ResponseEntity<ErrorResponse> handleUnrecognizedProperty(UnrecognizedPropertyException ex) {
        if ("branchId".equals(ex.getPropertyName())) {
            return build(HttpStatus.BAD_REQUEST, BranchIdNotAllowedException.CODE,
                    "branchId is not allowed in Wave 6 requests");
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Unknown field: " + ex.getPropertyName(),
                        Map.of("field", ex.getPropertyName())));
    }

    /**
     * Handles InvalidExpiryDateException.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidExpiryDateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidExpiryDate(InvalidExpiryDateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "value", ex.getValue())));
    }

    /**
     * Handle duplicate invoice number.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DuplicateInvoiceNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInvoiceNumber(DuplicateInvoiceNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("companyId", ex.getCompanyId(), "invoiceNumber", ex.getInvoiceNumber())));
    }

    /**
     * Handle duplicate receipt number.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DuplicateReceiptNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateReceiptNumber(DuplicateReceiptNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("companyId", ex.getCompanyId(), "receiptNumber", ex.getReceiptNumber())));
    }

    /**
     * Handle missing original document.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(MissingOriginalDocumentException.class)
    public ResponseEntity<ErrorResponse> handleMissingOriginalDocument(MissingOriginalDocumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    /**
     * Handle incompatible original document.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(IncompatibleOriginalDocumentException.class)
    public ResponseEntity<ErrorResponse> handleIncompatibleOriginalDocument(IncompatibleOriginalDocumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("expectedType", ex.getExpectedType(), "actualType", ex.getActualType())));
    }

    /**
     * Handle inconsistent totals.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(TotalsInconsistentException.class)
    public ResponseEntity<ErrorResponse> handleTotalsInconsistent(TotalsInconsistentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "expected", ex.getExpected(), "actual", ex.getActual())));
    }

    /**
     * Handle invalid unit-value fields.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidUnitValueException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUnitValue(InvalidUnitValueException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("field", ex.getField(), "missingKeys", ex.getMissingKeys())));
    }

    /**
     * Handle no certificate configured.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(NoCertificateConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleNoCertificateConfigured(NoCertificateConfiguredException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /**
     * Handle document-not-draft.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(DocumentNotDraftException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotDraft(DocumentNotDraftException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /**
     * Handle invalid lifecycle transition.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InvalidLifecycleTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLifecycleTransition(InvalidLifecycleTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("fromState", ex.getFromState(), "action", ex.getAction())));
    }

    /**
     * Handle optimistic-lock conflict from our service layer.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(OptimisticLockConflictException.class)
    public ResponseEntity<ConflictResponse> handleOptimisticLockConflict(OptimisticLockConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ConflictResponse(ex.getCode(), ex.getMessage(),
                        ex.getExpectedVersion(), ex.getActualVersion(),
                        ex.getCurrent()));
    }

    /**
     * Map JPA optimistic-lock failures to the same conflict shape.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleJpaOptimisticLock(Exception ex) {
        return build(HttpStatus.CONFLICT, OptimisticLockConflictException.CODE,
                "The document was modified by another user. Please refresh and retry.");
    }

    /**
     * Handle append-only DB-trigger violations.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(AppendOnlyViolationException.class)
    public ResponseEntity<ErrorResponse> handleAppendOnlyViolation(AppendOnlyViolationException ex) {
        log.error("Append-only violation on table: {}", ex.getTableName(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getCode(), ex.getMessage());
    }

    /**
     * Handle bulk-batch limit exceeded.
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(BulkBatchLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleBulkBatchLimitExceeded(BulkBatchLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("requested", ex.getRequested(), "limit", ex.getLimit())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    /**
     * Handles bean validation errors and returns structured field-level messages.
     *
     * @param ex the validation exception
     * @return the error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"))
                .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", "Validation failed",
                        Map.of("fieldErrors", fieldErrors)));
    }

    /**
     * Handle duplicate standard invoice number.
     *
     * @param ex the duplicate-number exception
     * @return 409 Conflict response carrying companyId + invoiceNumber
     */
    @ExceptionHandler(DuplicateStandardNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateStandardNumber(DuplicateStandardNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("companyId", ex.getCompanyId(), "invoiceNumber", ex.getInvoiceNumber())));
    }

    /**
     * Handle duplicate simplified invoice number.
     *
     * @param ex the duplicate-number exception
     * @return 409 Conflict response carrying companyId + invoiceNumber
     */
    @ExceptionHandler(DuplicateSimplifiedNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSimplifiedNumber(DuplicateSimplifiedNumberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("companyId", ex.getCompanyId(), "invoiceNumber", ex.getInvoiceNumber())));
    }

    /**
     * Handle missing buyer for standard invoice.
     *
     * @param ex the missing-buyer exception
     * @return 400 Bad Request response
     */
    @ExceptionHandler(MissingBuyerForStandardException.class)
    public ResponseEntity<ErrorResponse> handleMissingBuyerForStandard(MissingBuyerForStandardException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    /**
     * Handle VAT exemption reason required.
     *
     * @param ex the missing-exemption-reason exception
     * @return 400 Bad Request response carrying the VAT category code
     */
    @ExceptionHandler(VatExemptionReasonRequiredException.class)
    public ResponseEntity<ErrorResponse> handleVatExemptionReasonRequired(VatExemptionReasonRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("vatCategoryCode", ex.getVatCategoryCode())));
    }

    /**
     * Handle ZATCA chain-busy contention.
     *
     * @param ex the chain-busy exception
     * @return 503 Service Unavailable response
     */
    @ExceptionHandler(ChainBusyException.class)
    public ResponseEntity<ErrorResponse> handleChainBusy(ChainBusyException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getCode(), ex.getMessage());
    }

    /**
     * Handle wrong original document class.
     *
     * @param ex the wrong-class exception
     * @return 400 Bad Request response carrying expected + actual class
     */
    @ExceptionHandler(WrongOriginalClassException.class)
    public ResponseEntity<ErrorResponse> handleWrongOriginalClass(WrongOriginalClassException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("expectedClass", ex.getExpectedClass(), "actualClass", ex.getActualClass())));
    }

    /**
     * Handle invalid simplified transaction type.
     *
     * @param ex the invalid-transaction-type exception
     * @return 400 Bad Request response carrying the transactionTypeCode
     */
    @ExceptionHandler(InvalidSimplifiedTransactionTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSimplifiedTransactionType(
            InvalidSimplifiedTransactionTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("transactionTypeCode", ex.getTransactionTypeCode())));
    }

    /** Handle company not found (404).
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(CompanyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCompanyNotFound(CompanyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("registrationNumber", ex.getRegistrationNumber())));
    }

    /** Handle authority-environment not found (404).
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(AuthorityEnvironmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuthorityEnvironmentNotFound(AuthorityEnvironmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("authority", ex.getAuthority(), "environment", ex.getEnvironment())));
    }

    /** Handle archive write failure (503).
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(InboundPayloadArchiveException.class)
    public ResponseEntity<ErrorResponse> handleInboundPayloadArchiveFailure(InboundPayloadArchiveException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("cause", ex.getCause() != null
                        ? ex.getCause().getClass().getSimpleName() : "unknown")));
    }

    /**
     * Handle missing buyer identity (400).
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(BuyerIdentityRequiredException.class)
    public ResponseEntity<ErrorResponse> handleBuyerIdentityRequired(BuyerIdentityRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                ex.getCode(), ex.getMessage(),
                Map.of("buyerType", ex.getBuyerType(), "reason", ex.getReason())));
    }

    /**
     * Handle unparseable request bodies (400).
     *
     * @param ex the exception
     * @return the error response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null && cause.length() > 200) {
            cause = cause.substring(0, 200);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                "VALIDATION_ERROR", "Request body could not be parsed",
                Map.of("cause", cause != null ? cause : "unparseable body")));
    }

    /**
     * Honors the status carried by a {@link ResponseStatusException} (e.g. the
     * {@code 400} thrown by the Wave 9 read controllers for unparseable filter
     * parameters). Without this handler the catch-all below would swallow the
     * embedded status and report {@code 500}.
     *
     * @param ex the response-status exception
     * @return the error response with the embedded status
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        if (status.is5xxServerError()) {
            log.error("Response status exception", ex);
        }
        String code = status.is4xxClientError() ? "VALIDATION_ERROR" : "INTERNAL_ERROR";
        return build(status, code, reason);
    }

    /**
     * Catches all unhandled exceptions and returns a generic internal error response.
     *
     * @param ex the unhandled exception
     * @return the error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
