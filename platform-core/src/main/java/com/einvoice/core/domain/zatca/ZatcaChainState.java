package com.einvoice.core.domain.zatca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** Javadoc. */
@Entity
@Table(name = "zatca_chain_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZatcaChainState {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "authority_environment_id", nullable = false)
    private Short authorityEnvironmentId;

    @Column(name = "invoice_counter", nullable = false)
    @Builder.Default
    private Long invoiceCounter = 0L;

    @Column(name = "previous_invoice_hash")
    private String previousInvoiceHash;

    @UpdateTimestamp
    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;
}
