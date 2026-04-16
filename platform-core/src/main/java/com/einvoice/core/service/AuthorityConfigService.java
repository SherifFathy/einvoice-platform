package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceResetPolicy;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.BranchRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing authority configurations per branch.
 * Accepts plaintext sensitive data and encrypts it via {@link CryptoService}.
 */
@Service
public class AuthorityConfigService {

    private final AuthorityConfigRepository authorityConfigRepository;
    private final BranchRepository branchRepository;
    private final CryptoService cryptoService;

    /**
     * Creates the authority config service.
     *
     * @param authorityConfigRepository the authority config repository
     * @param branchRepository the branch repository
     * @param cryptoService the crypto service for encrypting sensitive fields
     */
    public AuthorityConfigService(AuthorityConfigRepository authorityConfigRepository,
            BranchRepository branchRepository, CryptoService cryptoService) {
        this.authorityConfigRepository = authorityConfigRepository;
        this.branchRepository = branchRepository;
        this.cryptoService = cryptoService;
    }

    /**
     * Creates a new authority configuration for a branch.
     *
     * @param branchId the branch identifier
     * @param authority the authority type
     * @param environment the environment
     * @param invoicePrefix the invoice prefix
     * @param invoiceStartingNumber the starting invoice number
     * @param invoiceResetPolicy the invoice reset policy
     * @return the created authority configuration
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "authority_config.create", entityType = "AuthorityConfig")
    public AuthorityConfig create(Long branchId, Authority authority, Environment environment,
            String invoicePrefix, Long invoiceStartingNumber,
            InvoiceResetPolicy invoiceResetPolicy) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BranchService.BranchNotFoundException(
                        "Branch not found: " + branchId));

        if (authorityConfigRepository.existsByBranchIdAndAuthorityAndEnvironment(
                branchId, authority, environment)) {
            throw new DuplicateAuthorityConfigException(
                    "Authority config already exists for branch " + branchId
                            + ", authority " + authority + ", environment " + environment);
        }

        AuthorityConfig config = AuthorityConfig.builder()
                .branch(branch)
                .authority(authority)
                .environment(environment)
                .invoicePrefix(invoicePrefix)
                .invoiceStartingNumber(invoiceStartingNumber != null ? invoiceStartingNumber : 1L)
                .invoiceResetPolicy(invoiceResetPolicy != null
                        ? invoiceResetPolicy : InvoiceResetPolicy.NEVER)
                .invoiceCounter(0L)
                .isActive(true)
                .enabledDocumentTypes("[]")
                .build();
        return authorityConfigRepository.save(config);
    }

    /**
     * Updates encrypted credential fields on an authority configuration.
     * Accepts plaintext bytes and encrypts them before persisting.
     *
     * @param configId the authority config identifier
     * @param credentials plaintext credentials to encrypt and store
     * @param certificate plaintext certificate to encrypt and store
     * @param csid plaintext CSID to encrypt and store
     * @param privateKey plaintext private key to encrypt and store
     * @param tokenData plaintext token data to encrypt and store
     * @return the updated authority configuration
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "authority_config.update_credentials",
            entityType = "AuthorityConfig",
            entityClass = AuthorityConfig.class)
    public AuthorityConfig updateEncryptedFields(Long configId, byte[] credentials,
            byte[] certificate, byte[] csid, byte[] privateKey, byte[] tokenData) {
        AuthorityConfig config = getById(configId);
        if (credentials != null) {
            config.setCredentialsEncrypted(cryptoService.encrypt(credentials));
        }
        if (certificate != null) {
            config.setCertificateEncrypted(cryptoService.encrypt(certificate));
        }
        if (csid != null) {
            config.setCsidEncrypted(cryptoService.encrypt(csid));
        }
        if (privateKey != null) {
            config.setPrivateKeyEncrypted(cryptoService.encrypt(privateKey));
        }
        if (tokenData != null) {
            config.setTokenDataEncrypted(cryptoService.encrypt(tokenData));
        }
        return config;
    }

    /**
     * Decrypts and returns the credentials for an authority configuration.
     *
     * @param configId the authority config identifier
     * @return the decrypted credentials bytes
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public byte[] decryptCredentials(Long configId) {
        AuthorityConfig config = getById(configId);
        return cryptoService.decrypt(config.getCredentialsEncrypted());
    }

    /**
     * Updates invoice numbering settings on an authority configuration.
     *
     * @param configId the authority config identifier
     * @param invoicePrefix the new invoice prefix
     * @param invoiceStartingNumber the new starting invoice number
     * @param invoiceResetPolicy the new invoice reset policy
     * @return the updated authority configuration
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    @Audited(action = "authority_config.update_settings",
            entityType = "AuthorityConfig",
            entityClass = AuthorityConfig.class)
    public AuthorityConfig updateInvoiceSettings(Long configId, String invoicePrefix,
            Long invoiceStartingNumber, InvoiceResetPolicy invoiceResetPolicy) {
        AuthorityConfig config = getById(configId);
        if (invoicePrefix != null) {
            config.setInvoicePrefix(invoicePrefix);
        }
        if (invoiceStartingNumber != null) {
            config.setInvoiceStartingNumber(invoiceStartingNumber);
        }
        if (invoiceResetPolicy != null) {
            config.setInvoiceResetPolicy(invoiceResetPolicy);
        }
        return config;
    }

    /**
     * Retrieves an authority configuration by its identifier.
     *
     * @param id the authority config identifier
     * @return the authority configuration
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public AuthorityConfig getById(Long id) {
        return authorityConfigRepository.findById(id)
                .orElseThrow(() -> new AuthorityConfigNotFoundException(
                        "Authority config not found: " + id));
    }

    /**
     * Lists all authority configurations for a branch.
     *
     * @param branchId the branch identifier
     * @return list of authority configurations
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public List<AuthorityConfig> listByBranch(Long branchId) {
        return authorityConfigRepository.findByBranchId(branchId);
    }

    /**
     * Retrieves an authority configuration by branch, authority, and environment.
     *
     * @param branchId the branch identifier
     * @param authority the authority type
     * @param environment the environment
     * @return the matching authority configuration
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public AuthorityConfig getByBranchAuthorityEnv(Long branchId, Authority authority,
            Environment environment) {
        return authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(branchId, authority, environment)
                .orElseThrow(() -> new AuthorityConfigNotFoundException(
                        "Authority config not found for branch " + branchId
                                + ", authority " + authority + ", environment " + environment));
    }

    /** Exception thrown when an authority configuration is not found. */
    public static class AuthorityConfigNotFoundException extends RuntimeException {
        /**
         * Creates a new AuthorityConfigNotFoundException.
         *
         * @param message the detail message
         */
        public AuthorityConfigNotFoundException(String message) {
            super(message);
        }
    }

    /** Exception thrown when a duplicate authority configuration is detected. */
    public static class DuplicateAuthorityConfigException extends RuntimeException {
        /**
         * Creates a new DuplicateAuthorityConfigException.
         *
         * @param message the detail message
         */
        public DuplicateAuthorityConfigException(String message) {
            super(message);
        }
    }
}
