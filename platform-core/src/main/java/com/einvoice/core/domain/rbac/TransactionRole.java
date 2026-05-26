package com.einvoice.core.domain.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Javadoc. */
@Entity
@Table(name = "transaction_roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRole {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 10)
    private String authority;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(name = "role_code", nullable = false, length = 30)
    private String roleCode;

    @Column(length = 255)
    private String description;
}
