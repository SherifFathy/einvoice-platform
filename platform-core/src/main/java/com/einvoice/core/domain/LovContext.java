package com.einvoice.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA entity representing a unique LOV context (authority + doc_type + sub_env combination). */
@Entity
@Table(name = "lov_contexts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LovContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authority", nullable = false, length = 10)
    private String authority;

    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "sub_env", nullable = false, length = 20)
    private String subEnv;

    @Column(name = "context_key", nullable = false, unique = true, length = 60)
    private String contextKey;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
