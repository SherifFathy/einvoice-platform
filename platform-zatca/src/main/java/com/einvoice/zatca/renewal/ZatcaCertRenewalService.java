package com.einvoice.zatca.renewal;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.service.AuthorityConfigService;
import com.einvoice.core.service.CryptoService;
import com.einvoice.zatca.client.ZatcaProductionCsidClient;
import com.einvoice.zatca.onboarding.ZatcaCsrGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.Map;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for renewing an existing ZATCA production certificate before expiry. */
@Service
public class ZatcaCertRenewalService {

    private static final Logger log = LoggerFactory.getLogger(ZatcaCertRenewalService.class);

    private final ZatcaCsrGenerator csrGenerator;
    private final ZatcaProductionCsidClient productionCsidClient;
    private final AuthorityConfigService authorityConfigService;
    private final CryptoService cryptoService;

    /**
     * Creates the renewal service.
     *
     * @param csrGenerator CSR generator for the renewal request
     * @param productionCsidClient ZATCA production CSID client
     * @param authorityConfigService authority config service
     * @param cryptoService crypto service for decrypting stored credentials
     */
    public ZatcaCertRenewalService(ZatcaCsrGenerator csrGenerator,
            ZatcaProductionCsidClient productionCsidClient,
            AuthorityConfigService authorityConfigService,
            CryptoService cryptoService) {
        this.csrGenerator = csrGenerator;
        this.productionCsidClient = productionCsidClient;
        this.authorityConfigService = authorityConfigService;
        this.cryptoService = cryptoService;
    }

    /** Thrown when no existing certificate is available for renewal. */
    public static class MissingCertificateException extends RuntimeException {
        /** @param message the detail message */
        public MissingCertificateException(String message) {
            super(message);
        }
    }

    public record RenewalResult(boolean success, OffsetDateTime newExpiryDate, String error) {}

    /**
     * Renews the ZATCA certificate for a branch by generating a new CSR and exchanging it.
     *
     * @param branchId the branch identifier
     * @param environment the target environment name
     * @return the renewal result with the new expiry date
     */
    @Transactional
    @Audited(action = "zatca.renewCertificate", entityType = "AuthorityConfig")
    public RenewalResult renewCertificate(Long branchId, String environment) {
        Environment env = Environment.valueOf(environment);

        AuthorityConfig config = authorityConfigService.getByBranchAuthorityEnv(
                branchId, Authority.ZATCA, env);

        if (config.getCsidEncrypted() == null) {
            throw new MissingCertificateException("No existing CSID found for renewal");
        }

        byte[] certBytes = config.getCertificateEncrypted() != null
                ? cryptoService.decrypt(config.getCertificateEncrypted()) : null;
        if (certBytes == null) {
            throw new MissingCertificateException("No existing certificate found for renewal");
        }

        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(certBytes));

            Map<String, String> rdns = parseSubjectRdns(cert);
            String commonName = rdns.getOrDefault("CN", "");
            String organizationUnit = rdns.getOrDefault("OU", "");
            String organization = rdns.getOrDefault("O", "");
            String country = rdns.getOrDefault("C", "SA");
            String serialNumber = rdns.get("SERIALNUMBER");
            if (serialNumber == null || serialNumber.isBlank()) {
                throw new IllegalStateException(
                        "Cannot renew: SERIALNUMBER missing from existing certificate");
            }

            ZatcaCsrGenerator.CsrResult csrResult = csrGenerator.generateCsr(
                    commonName, organizationUnit, organization, country, serialNumber);

            ZatcaProductionCsidClient.RenewalResult renewalResult =
                    productionCsidClient.renewCertificate(csrResult.csrBase64(), config);

            if (!renewalResult.success()) {
                return new RenewalResult(false, null, renewalResult.error());
            }

            byte[] newCsid = renewalResult.csid().getBytes(StandardCharsets.UTF_8);
            byte[] newSecret = renewalResult.secret().getBytes(StandardCharsets.UTF_8);
            byte[] newCertBytes = renewalResult.certificateBytes() != null
                    ? renewalResult.certificateBytes() : certBytes;

            authorityConfigService.updateEncryptedFields(config.getId(),
                    newSecret, newCertBytes, newCsid, csrResult.privateKeyDer(), null);

            OffsetDateTime newExpiry = renewalResult.certificateExpiry();
            if (newExpiry != null) {
                config.setCertificateExpiryDate(newExpiry);
                authorityConfigService.updateEncryptedFields(config.getId(),
                        null, null, null, null, null);
            }

            log.info("ZATCA certificate renewed for branch {} environment {}", branchId, environment);
            return new RenewalResult(true, newExpiry, null);
        } catch (MissingCertificateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Certificate renewal failed", e);
            return new RenewalResult(false, null, "Renewal failed: " + e.getMessage());
        }
    }

    private Map<String, String> parseSubjectRdns(X509Certificate cert) {
        try {
            LdapName ldapName = new LdapName(cert.getSubjectX500Principal().getName());
            Map<String, String> rdns = new java.util.HashMap<>();
            for (Rdn rdn : ldapName.getRdns()) {
                String type = rdn.getType().toUpperCase();
                if (!rdns.containsKey(type)) {
                    rdns.put(type, rdn.getValue().toString());
                }
            }
            return rdns;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse certificate subject DN", e);
        }
    }
}
