package com.einvoice.core.domain.eta;

import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
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

/** Javadoc. */
@Entity
@Table(name = "eta_receipt_headers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaReceiptHeader {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "receipt_number", nullable = false, length = 100)
    private String receiptNumber;

    @Column(name = "document_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private EtaReceiptDocumentType documentType;

    @Column(name = "document_type_version", nullable = false, length = 10)
    @Builder.Default
    private String documentTypeVersion = "1.2";

    @Column(name = "issue_datetime", nullable = false)
    private OffsetDateTime issueDatetime;

    @Column(name = "seller_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> sellerData;

    @Column(name = "buyer_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> buyerData;

    @Column(name = "pos_serial", length = 100)
    private String posSerial;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "EGP";

    @Column(name = "total_sales_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalSalesAmount = BigDecimal.ZERO;

    @Column(name = "total_commercial_discount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalCommercialDiscount = BigDecimal.ZERO;

    @Column(name = "extra_discount_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal extraDiscountAmount = BigDecimal.ZERO;

    @Column(name = "total_items_discount_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalItemsDiscountAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "exchange_rate", precision = 18, scale = 5)
    private BigDecimal exchangeRate;

    @Column(name = "previous_uuid", columnDefinition = "TEXT")
    private String previousUuid;

    @Column(name = "reference_old_uuid", columnDefinition = "TEXT")
    private String referenceOldUuid;

    @Column(name = "s_order_name_code", length = 200)
    private String sOrderNameCode;

    @Column(name = "order_delivery_mode", length = 30)
    private String orderDeliveryMode;

    @Column(name = "gross_weight", precision = 18, scale = 5)
    private BigDecimal grossWeight;

    @Column(name = "net_weight", precision = 18, scale = 5)
    private BigDecimal netWeight;

    @Column(name = "tax_totals", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> taxTotals;

    @Column(name = "extra_receipt_discount_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> extraReceiptDiscountData;

    @Column(name = "contractor_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> contractorData;

    @Column(name = "beneficiary_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> beneficiaryData;

    @Column(name = "fees_amount", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal feesAmount = BigDecimal.ZERO;

    @Column(name = "adjustment", precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal adjustment = BigDecimal.ZERO;

    @Column(name = "erp_reference_id", length = 100)
    private String erpReferenceId;

    @Column(name = "original_invoice_number", length = 100)
    private String originalInvoiceNumber;

    @Column(name = "eta_receipt_uuid", length = 255)
    private String etaReceiptUuid;

    @Column(name = "eta_submission_id", length = 255)
    private String etaSubmissionId;

    @Column(name = "original_receipt_id")
    private UUID originalReceiptId;

    @Column(name = "state", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentState state = DocumentState.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "header")
    @Builder.Default
    private List<EtaReceiptLine> lines = new ArrayList<>();
}
