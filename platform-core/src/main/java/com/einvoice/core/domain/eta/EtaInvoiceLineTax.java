package com.einvoice.core.domain.eta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Javadoc. */
@Entity
@Table(name = "eta_invoice_line_taxes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaInvoiceLineTax {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "line_id", nullable = false)
    private EtaInvoiceLine line;

    @Column(name = "tax_type", nullable = false, length = 30)
    private String taxType;

    @Column(name = "sub_type", length = 30)
    private String subType;

    @Column(name = "tax_rate", precision = 8, scale = 5)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 5)
    private BigDecimal taxAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
