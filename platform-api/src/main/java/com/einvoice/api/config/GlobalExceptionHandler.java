package com.einvoice.api.config;

import com.einvoice.core.exception.CompanyDeactivatedException;
import com.einvoice.core.exception.InvalidCredentialsException;
import com.einvoice.core.exception.InvalidRefreshTokenException;
import com.einvoice.core.exception.InvalidTransitionException;
import com.einvoice.core.exception.NoRoleInCompanyException;
import com.einvoice.core.service.AuthorityConfigService;
import com.einvoice.core.service.BranchService;
import com.einvoice.core.service.CompanyService;
import com.einvoice.core.service.CustomerService;
import com.einvoice.core.service.ItemService;
import com.einvoice.core.service.UserService;
import com.einvoice.core.service.InvoiceService;
import com.einvoice.core.service.importing.ImportException;
import com.einvoice.core.service.importing.TemplateException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Centralised exception-to-HTTP-response mapping for the REST API.
 * Converts domain-specific runtime exceptions into structured JSON error bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles {@link CompanyService.CompanyNotFoundException}.
     *
     * @param ex the caught exception
     * @return a 404 response with the error message
     */
    @ExceptionHandler(CompanyService.CompanyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCompanyNotFound(
            CompanyService.CompanyNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link BranchService.BranchNotFoundException}.
     *
     * @param ex the caught exception
     * @return a 404 response with the error message
     */
    @ExceptionHandler(BranchService.BranchNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleBranchNotFound(
            BranchService.BranchNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link UserService.UserNotFoundException}.
     *
     * @param ex the caught exception
     * @return a 404 response with the error message
     */
    @ExceptionHandler(UserService.UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            UserService.UserNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link UserService.UserNotAssignedException}.
     *
     * @param ex the caught exception
     * @return a 404 response with the error message
     */
    @ExceptionHandler(UserService.UserNotAssignedException.class)
    public ResponseEntity<Map<String, String>> handleUserNotAssigned(
            UserService.UserNotAssignedException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link CompanyService.DuplicateVatNumberException}.
     *
     * @param ex the caught exception
     * @return a 409 response with the error message
     */
    @ExceptionHandler(CompanyService.DuplicateVatNumberException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateVatNumber(
            CompanyService.DuplicateVatNumberException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles {@link UserService.UserAlreadyAssignedException}.
     *
     * @param ex the caught exception
     * @return a 409 response with the error message
     */
    @ExceptionHandler(UserService.UserAlreadyAssignedException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyAssigned(
            UserService.UserAlreadyAssignedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles {@link AuthorityConfigService.DuplicateAuthorityConfigException}.
     *
     * @param ex the caught exception
     * @return a 409 response with the error message
     */
    @ExceptionHandler(AuthorityConfigService.DuplicateAuthorityConfigException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateAuthorityConfig(
            AuthorityConfigService.DuplicateAuthorityConfigException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles {@link IllegalArgumentException}.
     *
     * @param ex the caught exception
     * @return a 400 response with the error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles {@link ResponseStatusException}.
     *
     * @param ex the caught exception
     * @return a response with the status and message from the exception
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(
            ResponseStatusException ex) {
        return buildResponse(
                HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(CompanyDeactivatedException.class)
    public ResponseEntity<Map<String, String>> handleCompanyDeactivated(
            CompanyDeactivatedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(NoRoleInCompanyException.class)
    public ResponseEntity<Map<String, String>> handleNoRoleInCompany(
            NoRoleInCompanyException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(CustomerService.CustomerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCustomerNotFound(
            CustomerService.CustomerNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CustomerService.DuplicateCustomerVatException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateCustomerVat(
            CustomerService.DuplicateCustomerVatException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CustomerService.B2bVatRequiredException.class)
    public ResponseEntity<Map<String, String>> handleB2bVatRequired(
            CustomerService.B2bVatRequiredException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles customer deletion blocked by invoice references.
     *
     * @param ex the caught exception
     * @return a 409 response with error message and linked invoices
     */
    @ExceptionHandler(CustomerService.CustomerReferencedByInvoiceException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerReferencedByInvoice(
            CustomerService.CustomerReferencedByInvoiceException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("linkedInvoices", ex.getLinkedInvoices());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ItemService.ItemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleItemNotFound(
            ItemService.ItemNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ItemService.DuplicateItemCodeException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateItemCode(
            ItemService.DuplicateItemCodeException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ImportException.class)
    public ResponseEntity<Map<String, String>> handleImportException(
            ImportException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TemplateException.class)
    public ResponseEntity<Map<String, String>> handleTemplateException(
            TemplateException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT,
                "The resource was modified by another user. Please refresh and try again.");
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(
            InvalidTransitionException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvoiceService.InvoiceNotDraftException.class)
    public ResponseEntity<Map<String, String>> handleInvoiceNotDraft(
            InvoiceService.InvoiceNotDraftException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles bean validation failures from {@code @Valid}-annotated request bodies.
     *
     * @param ex the caught exception
     * @return a 400 response with per-field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"))
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles authorization failures from Spring Security.
     *
     * @param ex the caught exception
     * @return a 403 response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * Handles unsupported HTTP methods.
     *
     * @param ex the caught exception
     * @return a 405 response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    /**
     * Handles unsupported media types.
     *
     * @param ex the caught exception
     * @return a 415 response
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.zatca.onboarding.ZatcaOnboardingService.OnboardingAlreadyCompletedException.class)
    public ResponseEntity<Map<String, String>> handleOnboardingAlreadyCompleted(
            com.einvoice.zatca.onboarding.ZatcaOnboardingService.OnboardingAlreadyCompletedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.zatca.onboarding.ZatcaOnboardingService.MissingAuthorityConfigException.class)
    public ResponseEntity<Map<String, String>> handleMissingAuthorityConfig(
            com.einvoice.zatca.onboarding.ZatcaOnboardingService.MissingAuthorityConfigException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.zatca.renewal.ZatcaCertRenewalService.MissingCertificateException.class)
    public ResponseEntity<Map<String, String>> handleMissingCertificate(
            com.einvoice.zatca.renewal.ZatcaCertRenewalService.MissingCertificateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.eta.codes.DuplicateEtaItemCodeException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEtaItemCode(
            com.einvoice.eta.codes.DuplicateEtaItemCodeException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.eta.codes.EtaItemCodeNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEtaItemCodeNotFound(
            com.einvoice.eta.codes.EtaItemCodeNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.eta.codes.EtaItemCodeAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleEtaItemCodeAccessDenied(
            com.einvoice.eta.codes.EtaItemCodeAccessDeniedException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(com.einvoice.eta.codes.EtaSearchException.class)
    public ResponseEntity<Map<String, String>> handleEtaSearch(
            com.einvoice.eta.codes.EtaSearchException ex) {
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred");
    }

    private ResponseEntity<Map<String, String>> buildResponse(
            HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
