package com.einvoice.api.error;

import com.einvoice.core.error.AssignmentExistsException;
import com.einvoice.core.error.BranchCodeDuplicateException;
import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.core.error.EmailAlreadyExistsException;
import com.einvoice.core.error.InactiveCompanyException;
import com.einvoice.core.error.InvalidAuthorityEnvironmentException;
import com.einvoice.core.error.InvalidRoleForAuthorityException;
import com.einvoice.core.error.LastSuperUserProtectedException;
import com.einvoice.core.error.TaxNumberDuplicateException;
import com.einvoice.core.error.UnauthorizedContextException;
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
