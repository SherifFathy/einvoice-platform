package com.einvoice.api.error;

import com.einvoice.core.error.AssignmentExistsException;
import com.einvoice.core.error.BranchCodeDuplicateException;
import com.einvoice.core.error.BranchIdNotAllowedException;
import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.core.error.ConfigNotFoundException;
import com.einvoice.core.error.CustomerNotFoundException;
import com.einvoice.core.error.DuplicateInternalCodeException;
import com.einvoice.core.error.DuplicateTaxNumberException;
import com.einvoice.core.error.DuplicateVatNumberException;
import com.einvoice.core.error.EmailAlreadyExistsException;
import com.einvoice.core.error.InactiveCompanyException;
import com.einvoice.core.error.InvalidAddressDataException;
import com.einvoice.core.error.InvalidAuthorityEnvironmentException;
import com.einvoice.core.error.InvalidAuthorityForRouteException;
import com.einvoice.core.error.InvalidCustomerTypeException;
import com.einvoice.core.error.InvalidEnvironmentForAuthorityException;
import com.einvoice.core.error.InvalidExpiryDateException;
import com.einvoice.core.error.InvalidItemTypeException;
import com.einvoice.core.error.InvalidRoleForAuthorityException;
import com.einvoice.core.error.InvalidVatCategoryException;
import com.einvoice.core.error.InvalidVatNumberException;
import com.einvoice.core.error.InvalidVatRateException;
import com.einvoice.core.error.ItemNotFoundException;
import com.einvoice.core.error.LastSuperUserProtectedException;
import com.einvoice.core.error.ProductionTokenRequiredException;
import com.einvoice.core.error.TaxNumberDuplicateException;
import com.einvoice.core.error.UnauthorizedContextException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
