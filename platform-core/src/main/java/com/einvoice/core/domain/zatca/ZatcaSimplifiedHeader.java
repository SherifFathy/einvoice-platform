package com.einvoice.core.domain.zatca;

import com.einvoice.core.domain.shared.DocumentState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** JPA entity for ZATCA simplified (B2C) invoice headers. */
@Entity
@Table(name = "zatca_simplified_headers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZatcaSimplifiedHeader {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "invoice_number", nullable = false, length = 100)
    private String invoiceNumber;

    @Column(name = "zatca_uuid", length = 255)
    private String zatcaUuid;

    @Column(name = "invoice_type_code", nullable = false, length = 10)
    @Builder.Default
    private String invoiceTypeCode = "388";

    @Column(name = "transaction_type_code", nullable = false, length = 10)
    private String transactionTypeCode;

    @Column(name = "business_process_code", length = 40)
    @Builder.Default
    private String businessProcessCode = "reporting:1.0";

    @Column(name = "issuance_reason", length = 127)
    private String issuanceReason;

    @Column(name = "billing_reference_id", length = 100)
    private String billingReferenceId;

    @Column(name = "original_invoice_number", length = 100)
    private String originalInvoiceNumber;

    @Column(name = "erp_reference_id", length = 100)
    private String erpReferenceId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "issue_time", nullable = false)
    private LocalTime issueTime;

    @Column(name = "supply_date")
    private LocalDate supplyDate;

    @Column(name = "supply_end_date")
    private LocalDate supplyEndDate;

    @Column(name = "seller_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> sellerData;

    @Column(name = "seller_party_id", length = 50)
    private String sellerPartyId;

    @Column(name = "seller_party_id_scheme", length = 10)
    private String sellerPartyIdScheme;

    @Column(name = "seller_vat_number", length = 15)
    private String sellerVatNumber;

    @Column(name = "seller_group_vat_number", length = 15)
    private String sellerGroupVatNumber;

    @Column(name = "seller_building_number", length = 4)
    private String sellerBuildingNumber;

    @Column(name = "seller_additional_number", length = 4)
    private String sellerAdditionalNumber;

    @Column(name = "seller_postal_code", length = 5)
    private String sellerPostalCode;

    @Column(name = "seller_country_code", length = 2)
    @Builder.Default
    private String sellerCountryCode = "SA";

    @Column(name = "buyer_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> buyerData;

    @Column(name = "buyer_party_id", length = 50)
    private String buyerPartyId;

    @Column(name = "buyer_party_id_scheme", length = 10)
    private String buyerPartyIdScheme;

    @Column(name = "buyer_vat_number", length = 15)
    private String buyerVatNumber;

    @Column(name = "buyer_group_vat_number", length = 15)
    private String buyerGroupVatNumber;

    @Column(name = "buyer_building_number", length = 4)
    private String buyerBuildingNumber;

    @Column(name = "buyer_additional_number", length = 4)
    private String buyerAdditionalNumber;

    @Column(name = "buyer_postal_code", length = 5)
    private String buyerPostalCode;

    @Column(name = "buyer_country_code", length = 2)
    private String buyerCountryCode;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "SAR";

    @Column(name = "tax_currency", length = 3)
    @Builder.Default
    private String taxCurrency = "SAR";

    @Column(name = "tax_amount_accounting_currency", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxAmountAccountingCurrency = BigDecimal.ZERO;

    @Column(name = "rounding_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal roundingAmount = BigDecimal.ZERO;

    @Column(name = "payment_means_code", length = 3)
    private String paymentMeansCode;

    @Column(name = "payment_means_text", length = 50)
    private String paymentMeansText;

    @Column(name = "line_extension_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal lineExtensionAmount = BigDecimal.ZERO;

    @Column(name = "allowance_total_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal allowanceTotalAmount = BigDecimal.ZERO;

    @Column(name = "tax_exclusive_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxExclusiveAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "tax_inclusive_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxInclusiveAmount = BigDecimal.ZERO;

    @Column(name = "prepaid_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal prepaidAmount = BigDecimal.ZERO;

    @Column(name = "payable_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal payableAmount = BigDecimal.ZERO;

    @Column(name = "invoice_counter_value")
    private Long invoiceCounterValue;

    @Column(name = "previous_invoice_hash", columnDefinition = "TEXT")
    private String previousInvoiceHash;

    @Column(name = "invoice_hash", columnDefinition = "TEXT")
    private String invoiceHash;

    @Column(name = "qr_code_base64", columnDefinition = "TEXT")
    private String qrCodeBase64;

    @Column(name = "reporting_status", length = 50)
    private String reportingStatus;

    @Column(name = "zatca_response_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> zatcaResponseData;

    @Column(name = "original_invoice_id")
    private UUID originalInvoiceId;

    @Column(name = "status", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentState status = DocumentState.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "header")
    @Builder.Default
    private List<ZatcaSimplifiedLine> lines = new ArrayList<>();
}
