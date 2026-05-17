package com.einvoice.core.domain.eta;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
import org.hibernate.type.SqlTypes;

/** Javadoc. */
@Entity
@Table(name = "eta_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaReceiptLine {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "header_id", nullable = false)
    private EtaReceiptHeader header;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "internal_code", length = 100)
    private String internalCode;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType;

    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_type", nullable = false, length = 50)
    private String unitType;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 5)
    private BigDecimal quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unit_value", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> unitValue;

    @Column(name = "sales_total", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal salesTotal = BigDecimal.ZERO;

    @Column(name = "discount_rate", precision = 8, scale = 5)
    private BigDecimal discountRate;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "items_discount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal itemsDiscount = BigDecimal.ZERO;

    @Column(name = "value_difference", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal valueDifference = BigDecimal.ZERO;

    @Column(name = "total_taxable_fees", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal totalTaxableFees = BigDecimal.ZERO;

    @Column(name = "net_total", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal netTotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 18, scale = 5)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "line")
    @Builder.Default
    private List<EtaReceiptLineTax> taxes = new ArrayList<>();
}
