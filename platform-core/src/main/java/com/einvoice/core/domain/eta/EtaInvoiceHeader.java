package com.einvoice.core.domain.eta;

import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
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
@Table(name = "eta_invoice_headers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaInvoiceHeader {

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

    @Column(name = "document_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private EtaInvoiceDocumentType documentType;

    @Column(name = "document_type_version", nullable = false, length = 10)
    @Builder.Default
    private String documentTypeVersion = "1.0";

    @Column(name = "issue_datetime", nullable = false)
    private OffsetDateTime issueDatetime;

    @Column(name = "service_delivery_date")
    private LocalDate serviceDeliveryDate;

    @Column(name = "seller_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> sellerData;

    @Column(name = "buyer_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> buyerData;

    @Column(name = "taxpayer_activity_code", length = 50)
    private String taxpayerActivityCode;

    @Column(name = "purchase_order_reference", length = 100)
    private String purchaseOrderReference;

    @Column(name = "purchase_order_description", columnDefinition = "TEXT")
    private String purchaseOrderDescription;

    @Column(name = "sales_order_reference", length = 100)
    private String salesOrderReference;

    @Column(name = "sales_order_description", columnDefinition = "TEXT")
    private String salesOrderDescription;

    @Column(name = "proforma_invoice_number", length = 50)
    private String proformaInvoiceNumber;

    @Column(name = "payment_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> paymentData;

    @Column(name = "delivery_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> deliveryData;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "EGP";

    @Column(name = "total_sales_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalSalesAmount = BigDecimal.ZERO;

    @Column(name = "total_discount_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalDiscountAmount = BigDecimal.ZERO;

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

    @Column(name = "eta_uuid", length = 255)
    private String etaUuid;

    @Column(name = "eta_long_id", length = 255)
    private String etaLongId;

    @Column(name = "eta_submission_id", length = 255)
    private String etaSubmissionId;

    @Column(name = "original_invoice_number", length = 100)
    private String originalInvoiceNumber;

    @Column(name = "original_document_id")
    private UUID originalDocumentId;

    @Column(name = "erp_reference_id", length = 100)
    private String erpReferenceId;

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
    private List<EtaInvoiceLine> lines = new ArrayList<>();
}
