package com.einvoice.api.support;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.config.ZatcaConfig;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.AuditLog;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.core.repository.shared.AuditLogRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedHeaderRepository;
import com.einvoice.core.repository.zatca.ZatcaStandardHeaderRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Reusable Wave 9 seed fixtures for the dashboard, submission-log, and audit
 * contract/integration tests. Pure test-support: every method inserts minimal
 * rows using obvious placeholder secrets so later stories (US1/US2/US3/US5)
 * can assert against the created ids.
 *
 * <p>Canonical test environment ids (migration {@code V37}, confirm before
 * hardcoding in a test): {@code ETA PREPROD = 2}, {@code ZATCA SANDBOX = 5}.
 *
 * <p>Field-name reminder: ETA headers expose {@code state} with a version of
 * type {@code Integer}, while ZATCA headers expose {@code status} with a
 * version of type {@code Long}; both share {@link DocumentState}. Also note
 * {@code SubmissionAttempt.submittedAt} is a JPA creation timestamp and cannot
 * be set through the entity builder, so use {@link #seedAttemptAt} whenever a
 * specific timestamp (today vs this-month KPI boundary) is required.
 *
 * <p>No real key or certificate material is ever seeded — only obvious
 * placeholders (Constitution VI/XVIII).
 */
@Component
public class Wave9Fixtures {

    private static final String DEFAULT_ROLE_CODE = "ACCOUNTANT";

    private static final String PLACEHOLDER_SECRET =
            "TEST-ONLY-PLACEHOLDER-NEVER-REAL-KEY-MATERIAL";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ZatcaConfigRepository zatcaConfigRepository;
    private final EtaInvoiceHeaderRepository etaInvoiceHeaderRepository;
    private final EtaReceiptHeaderRepository etaReceiptHeaderRepository;
    private final ZatcaStandardHeaderRepository zatcaStandardHeaderRepository;
    private final ZatcaSimplifiedHeaderRepository zatcaSimplifiedHeaderRepository;
    private final SubmissionAttemptRepository submissionAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    private final AtomicInteger numberSequence = new AtomicInteger();

    /**
     * Constructs the fixture with the repositories and template it seeds through.
     *
     * @param companyRepository company persistence
     * @param userRepository user persistence
     * @param zatcaConfigRepository ZATCA config persistence
     * @param etaInvoiceHeaderRepository ETA invoice header persistence
     * @param etaReceiptHeaderRepository ETA receipt header persistence
     * @param zatcaStandardHeaderRepository ZATCA standard header persistence
     * @param zatcaSimplifiedHeaderRepository ZATCA simplified header persistence
     * @param submissionAttemptRepository submission attempt persistence
     * @param auditLogRepository audit log persistence (append-only writes)
     * @param jdbcTemplate raw SQL for role grants and timestamped attempts
     * @param passwordEncoder password hashing for seeded users
     */
    public Wave9Fixtures(CompanyRepository companyRepository,
            UserRepository userRepository,
            ZatcaConfigRepository zatcaConfigRepository,
            EtaInvoiceHeaderRepository etaInvoiceHeaderRepository,
            EtaReceiptHeaderRepository etaReceiptHeaderRepository,
            ZatcaStandardHeaderRepository zatcaStandardHeaderRepository,
            ZatcaSimplifiedHeaderRepository zatcaSimplifiedHeaderRepository,
            SubmissionAttemptRepository submissionAttemptRepository,
            AuditLogRepository auditLogRepository,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.zatcaConfigRepository = zatcaConfigRepository;
        this.etaInvoiceHeaderRepository = etaInvoiceHeaderRepository;
        this.etaReceiptHeaderRepository = etaReceiptHeaderRepository;
        this.zatcaStandardHeaderRepository = zatcaStandardHeaderRepository;
        this.zatcaSimplifiedHeaderRepository = zatcaSimplifiedHeaderRepository;
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.auditLogRepository = auditLogRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates and persists an active company. Use this for ETA scenarios where
     * the dashboard certificate status must be null (no ZatcaConfig is seeded).
     *
     * @param nameEn English company name
     * @param taxNumber company tax number
     * @return the persisted company with its generated id
     */
    public Company createCompany(String nameEn, String taxNumber) {
        return companyRepository.save(Company.builder()
                .nameEn(nameEn)
                .nameAr("شركة")
                .taxNumber(taxNumber)
                .isActive(true)
                .build());
    }

    /**
     * Creates and persists an active, non-super user with a hashed password.
     *
     * @param email unique login email
     * @return the persisted user with its generated id
     */
    public User createRegularUser(String email) {
        return userRepository.save(User.builder()
                .name("Wave9 Operator")
                .email(email)
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(false)
                .isActive(true)
                .build());
    }

    /**
     * Grants an active transaction role to a user on a company in an authority
     * environment. Inserted via raw SQL to mirror
     * {@code ZatcaIsolationTestSupport.grantRole}.
     *
     * @param userId the grantee
     * @param companyId the company the role is scoped to
     * @param envId the authority environment id
     * @param transactionType one of INVOICE/RECEIPT/STANDARD/SIMPLIFIED
     * @param roleCode the role code (e.g. ACCOUNTANT)
     */
    public void grantRole(UUID userId, UUID companyId, short envId,
            String transactionType, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_code) "
                        + "VALUES (?, ?, ?, ?, ?)",
                userId, companyId, envId, transactionType, roleCode);
    }

    /** Holder for the operator and two companies created by seedAssignedCompanies. */
    public record AssignedCompanies(
            User user, Company companyA, Company companyB) {
    }

    /**
     * Creates one active operator and two active companies, then grants the
     * operator an active role on EACH company in the given environment for
     * every supplied transaction type. Proves the dashboard "assigned-only"
     * multi-company scoping used by US1/US3.
     *
     * @param envId the active authority environment both companies live in
     * @param transactionTypes roles to grant on each company (e.g.
     *        {@code "STANDARD"}, {@code "SIMPLIFIED"} for a ZATCA env, or
     *        {@code "INVOICE"}, {@code "RECEIPT"} for an ETA env)
     * @return the created user and both companies
     */
    public AssignedCompanies seedAssignedCompanies(short envId,
            String... transactionTypes) {
        User user = createRegularUser(
                "wave9-" + nextNumber() + "@test.com");
        Company companyA = createCompany("Wave9 Co A", uniqueTaxNumber());
        Company companyB = createCompany("Wave9 Co B", uniqueTaxNumber());
        for (String txType : transactionTypes) {
            grantRole(user.getId(), companyA.getId(), envId, txType,
                    DEFAULT_ROLE_CODE);
            grantRole(user.getId(), companyB.getId(), envId, txType,
                    DEFAULT_ROLE_CODE);
        }
        return new AssignedCompanies(user, companyA, companyB);
    }

    /**
     * Creates and persists an active ZatcaConfig with the given certificate
     * expiry date. Only obvious placeholder values are used for the key, csr,
     * and certificate material — never real secrets (Constitution VI/XVIII).
     *
     * @param companyId the owning company
     * @param envId the ZATCA authority environment id
     * @param certificateExpiryDate the cert expiry read by the evaluator
     * @return the persisted config with its generated id
     */
    public ZatcaConfig seedZatcaConfig(UUID companyId, short envId,
            LocalDate certificateExpiryDate) {
        return zatcaConfigRepository.save(ZatcaConfig.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .privateKey(PLACEHOLDER_SECRET)
                .deviceUuid("test-device-" + nextNumber())
                .csr(PLACEHOLDER_SECRET)
                .complianceCertificate(PLACEHOLDER_SECRET)
                .complianceApiSecret(PLACEHOLDER_SECRET)
                .certificateExpiryDate(certificateExpiryDate)
                .isActive(true)
                .build());
    }

    /**
     * Convenience wrapper that sets the certificate to expire in the given
     * number of days from today. Pass a value under 30 to trigger the
     * dashboard "expiring soon" warning (FR-003).
     *
     * @param companyId the owning company
     * @param envId the ZATCA authority environment id
     * @param daysToExpiry days until the seeded certificate expires
     * @return the persisted config with its generated id
     */
    public ZatcaConfig seedZatcaConfigExpiringIn(UUID companyId, short envId,
            int daysToExpiry) {
        return seedZatcaConfig(companyId, envId,
                LocalDate.now().plusDays(daysToExpiry));
    }

    /**
     * Creates and persists an ETA invoice header in the given state. ETA uses
     * the {@code state} column (version Integer).
     *
     * @param companyId owning company
     * @param envId ETA authority environment id
     * @param state document lifecycle state
     * @param invoiceNumber unique invoice number
     * @return the persisted header with its generated id
     */
    public EtaInvoiceHeader seedEtaInvoice(UUID companyId, short envId,
            DocumentState state, String invoiceNumber) {
        return etaInvoiceHeaderRepository.save(EtaInvoiceHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .invoiceNumber(invoiceNumber)
                .documentType(EtaInvoiceDocumentType.i)
                .issueDatetime(OffsetDateTime.now())
                .sellerData(party("Seller"))
                .buyerData(party("Buyer"))
                .state(state)
                .build());
    }

    /**
     * Creates and persists an ETA receipt header in the given state. ETA uses
     * the {@code state} column (version Integer).
     *
     * @param companyId owning company
     * @param envId ETA authority environment id
     * @param state document lifecycle state
     * @param receiptNumber unique receipt number
     * @return the persisted header with its generated id
     */
    public EtaReceiptHeader seedEtaReceipt(UUID companyId, short envId,
            DocumentState state, String receiptNumber) {
        return etaReceiptHeaderRepository.save(EtaReceiptHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .receiptNumber(receiptNumber)
                .documentType(EtaReceiptDocumentType.r)
                .issueDatetime(OffsetDateTime.now())
                .sellerData(party("Seller"))
                .state(state)
                .build());
    }

    /**
     * Creates and persists a ZATCA standard (B2B) header in the given state.
     * ZATCA uses the {@code status} column (version Long).
     *
     * @param companyId owning company
     * @param envId ZATCA authority environment id
     * @param state document lifecycle state
     * @param invoiceNumber unique invoice number
     * @return the persisted header with its generated id
     */
    public ZatcaStandardHeader seedZatcaStandard(UUID companyId, short envId,
            DocumentState state, String invoiceNumber) {
        return zatcaStandardHeaderRepository.save(ZatcaStandardHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .invoiceNumber(invoiceNumber)
                .transactionTypeCode("0100000")
                .issueDate(LocalDate.now())
                .issueTime(LocalTime.of(12, 0))
                .sellerData(party("Seller"))
                .buyerData(party("Buyer"))
                .status(state)
                .build());
    }

    /**
     * Creates and persists a ZATCA simplified (B2C) header in the given state.
     * ZATCA uses the {@code status} column (version Long).
     *
     * @param companyId owning company
     * @param envId ZATCA authority environment id
     * @param state document lifecycle state
     * @param invoiceNumber unique invoice number
     * @return the persisted header with its generated id
     */
    public ZatcaSimplifiedHeader seedZatcaSimplified(UUID companyId, short envId,
            DocumentState state, String invoiceNumber) {
        return zatcaSimplifiedHeaderRepository.save(ZatcaSimplifiedHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .invoiceNumber(invoiceNumber)
                .transactionTypeCode("0200000")
                .issueDate(LocalDate.now())
                .issueTime(LocalTime.of(12, 0))
                .sellerData(party("Seller"))
                .status(state)
                .build());
    }

    /** Holder for the documents seeded by seedMixedHeaders, grouped by class. */
    public record MixedHeaders(
            List<EtaInvoiceHeader> etaInvoices,
            List<EtaReceiptHeader> etaReceipts,
            List<ZatcaStandardHeader> zatcaStandard,
            List<ZatcaSimplifiedHeader> zatcaSimplified) {
    }

    /**
     * Seeds documents across all four classes for one company and environment
     * in a mixed state spread covering SUBMITTING, SUBMITTED, IN_REVIEW,
     * REJECTED, and ACCEPTED, plus at least one DRAFT so the "counts exclude
     * DRAFT" rule can be exercised. Callers doing authority-specific dashboard
     * count assertions should prefer the per-class seeders for the relevant
     * authority's modules.
     *
     * @param companyId owning company
     * @param envId authority environment id
     * @return the created headers grouped by class
     */
    public MixedHeaders seedMixedHeaders(UUID companyId, short envId) {
        List<EtaInvoiceHeader> invoices = List.of(
                seedEtaInvoice(companyId, envId, DocumentState.SUBMITTING,
                        "W9-ETA-INV-" + nextNumber()),
                seedEtaInvoice(companyId, envId, DocumentState.ACCEPTED,
                        "W9-ETA-INV-" + nextNumber()),
                seedEtaInvoice(companyId, envId, DocumentState.DRAFT,
                        "W9-ETA-INV-" + nextNumber()));
        List<EtaReceiptHeader> receipts = List.of(
                seedEtaReceipt(companyId, envId, DocumentState.SUBMITTED,
                        "W9-ETA-REC-" + nextNumber()),
                seedEtaReceipt(companyId, envId, DocumentState.REJECTED,
                        "W9-ETA-REC-" + nextNumber()));
        List<ZatcaStandardHeader> standard = List.of(
                seedZatcaStandard(companyId, envId, DocumentState.IN_REVIEW,
                        "W9-ZSD-INV-" + nextNumber()),
                seedZatcaStandard(companyId, envId, DocumentState.ACCEPTED,
                        "W9-ZSD-INV-" + nextNumber()));
        List<ZatcaSimplifiedHeader> simplified = List.of(
                seedZatcaSimplified(companyId, envId, DocumentState.DRAFT,
                        "W9-ZSM-INV-" + nextNumber()));
        return new MixedHeaders(invoices, receipts, standard, simplified);
    }

    /**
     * Seeds a submission attempt via the entity, so {@code submittedAt} is
     * stamped "now" by JPA. Pass a {@code null} result for an in-flight
     * attempt (surfaces as IN_FLIGHT later). Each call uses a fresh random
     * document id at attempt number 1, so the unique
     * {@code (document_id, attempt_number)} constraint is always satisfied.
     *
     * @param companyId owning company
     * @param envId authority environment id
     * @param type transaction class
     * @param result outcome, or {@code null} for in-flight
     * @return the persisted attempt with its generated id
     */
    public SubmissionAttempt seedAttempt(UUID companyId, short envId,
            TransactionType type, SubmissionResult result) {
        return submissionAttemptRepository.save(SubmissionAttempt.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .transactionType(type)
                .documentId(UUID.randomUUID())
                .attemptNumber(1)
                .result(result)
                .build());
    }

    /**
     * Seeds a submission attempt for a specific document and attempt number
     * (use this to attach multiple attempts to one document). {@code submittedAt}
     * is stamped "now" by JPA — use {@link #seedAttemptAt} for a custom
     * timestamp.
     *
     * @param companyId owning company
     * @param envId authority environment id
     * @param type transaction class
     * @param result outcome, or {@code null} for in-flight
     * @param documentId the document this attempt transmits
     * @param attemptNumber 1-based attempt sequence for the document
     * @return the persisted attempt with its generated id
     */
    public SubmissionAttempt seedAttempt(UUID companyId, short envId,
            TransactionType type, SubmissionResult result, UUID documentId,
            int attemptNumber) {
        return submissionAttemptRepository.save(SubmissionAttempt.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .transactionType(type)
                .documentId(documentId)
                .attemptNumber(attemptNumber)
                .result(result)
                .build());
    }

    /**
     * Seeds a submission attempt with an explicit {@code submitted_at} via raw
     * SQL. Required because {@code submitted_at} is a JPA creation timestamp
     * and cannot be set through the entity builder; use this for today vs
     * this-month KPI boundary tests (US1). Returns the generated attempt id.
     *
     * @param companyId owning company
     * @param envId authority environment id
     * @param type transaction class
     * @param result outcome, or {@code null} for in-flight
     * @param submittedAt the exact submission timestamp to seed
     * @return the generated attempt id
     */
    public UUID seedAttemptAt(UUID companyId, short envId, TransactionType type,
            SubmissionResult result, OffsetDateTime submittedAt) {
        UUID id = UUID.randomUUID();
        String resultValue = (result == null) ? null : result.name();
        jdbcTemplate.update(
                "INSERT INTO submission_attempts (id, company_id, "
                        + "authority_environment_id, transaction_type, "
                        + "document_id, attempt_number, result, submitted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, companyId, envId, type.name(), UUID.randomUUID(),
                1, resultValue, submittedAt);
        return id;
    }

    /**
     * Seeds an append-only audit-log row directly via the repository for the
     * given company and environment. Use this for the audit-log read-path
     * scoping tests (US4). {@code payloadBefore}/{@code payloadAfter} default
     * to small placeholder maps so the expandable detail view has content.
     *
     * @param companyId owning company (may be null for cross-company actions)
     * @param envId authority environment id (the hard read boundary)
     * @param action audited action label
     * @param entityType audited entity type
     * @param entityId audited entity id
     * @return the persisted audit log with its generated id
     */
    public AuditLog seedAuditLog(UUID companyId, short envId, String action,
            String entityType, String entityId) {
        return auditLogRepository.save(AuditLog.builder()
                .companyId(companyId)
                .authorityEnvironmentId(envId)
                .userId(UUID.randomUUID())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .payloadBefore(Map.of("placeholder", "before"))
                .payloadAfter(Map.of("placeholder", "after"))
                .ipAddress("127.0.0.1")
                .build());
    }

    private int nextNumber() {
        return numberSequence.incrementAndGet();
    }

    private String uniqueTaxNumber() {
        return "300000000" + String.format("%04d", nextNumber());
    }

    private Map<String, Object> party(String name) {
        return Map.of("partyName", name);
    }
}
