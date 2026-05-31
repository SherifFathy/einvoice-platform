package com.einvoice.core.domain.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Javadoc. */
@Entity
@Table(name = "authority_environments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorityEnvironment {

    @Id
    private Short id;

    @Column(nullable = false, length = 10)
    private String authority;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
