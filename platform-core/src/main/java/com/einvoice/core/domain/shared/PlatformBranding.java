package com.einvoice.core.domain.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row platform-wide branding settings. */
@Entity
@Table(name = "platform_branding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBranding {

    /** Single-row identifier. */
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "logo_path", length = 512)
    private String logoPath;

    @Column(name = "logo_mime", length = 100)
    private String logoMime;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
