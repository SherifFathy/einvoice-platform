package com.einvoice.core.domain;

import com.einvoice.core.domain.enums.CustomerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Buyer entity scoped to a company. Supports soft delete. */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "lov_context_id", nullable = false)
    private Long lovContextId;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "vat_number")
    private String vatNumber;

    @Column(name = "id_type")
    private String idType;

    @Column(name = "id_value")
    private String idValue;

    @Column(name = "street")
    private String street;

    @Column(name = "building_number")
    private String buildingNumber;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country_code")
    @Builder.Default
    private String countryCode = "SA";

    @Column(name = "customer_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CustomerType customerType;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
