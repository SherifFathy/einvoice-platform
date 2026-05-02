package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Financial document (draft only in Wave 1). */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @Column(updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "lov_context_id", nullable = false)
    private Long lovContextId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private InvoiceType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subtype_flags", columnDefinition = "jsonb")
    @Builder.Default
    private String subtypeFlags = "{}";

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "supply_date")
    private LocalDate supplyDate;

    @Column(name = "supply_end_date")
    private LocalDate supplyEndDate;

    @Column(length = 3)
    @Builder.Default
    private String currency = "SAR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private Customer buyer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seller_data", columnDefinition = "jsonb")
    private String sellerData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buyer_data", columnDefinition = "jsonb")
    private String buyerData;

    @Column(name = "payment_means_code", length = 5)
    private String paymentMeansCode;

    @Column(name = "payment_terms", columnDefinition = "text")
    private String paymentTerms;

    @Column(name = "prepaid_amount", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal prepaidAmount = BigDecimal.ZERO;

    @Column(name = "total_line_net", precision = 18, scale = 2)
    private BigDecimal totalLineNet;

    @Column(name = "total_allowances", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAllowances = BigDecimal.ZERO;

    @Column(name = "total_without_vat", precision = 18, scale = 2)
    private BigDecimal totalWithoutVat;

    @Column(name = "total_vat", precision = 18, scale = 2)
    private BigDecimal totalVat;

    @Column(name = "total_with_vat", precision = 18, scale = 2)
    private BigDecimal totalWithVat;

    @Column(name = "amount_due", precision = 18, scale = 2)
    private BigDecimal amountDue;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Authority authority;

    @Column(nullable = false, length = 25)
    @Enumerated(EnumType.STRING)
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private Invoice originalInvoice;

    @Column(name = "external_invoice_reference", columnDefinition = "text")
    private String externalInvoiceReference;

    @Column(columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceVatBreakdown> vatBreakdown = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
