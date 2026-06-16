package com.einvoice.api.platform;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.platform.dto.PlatformBrandingResponse;
import com.einvoice.core.domain.shared.PlatformBranding;
import com.einvoice.core.repository.shared.PlatformBrandingRepository;
import com.einvoice.security.tenant.TenantContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Stores, validates, and serves the single platform-wide logo. */
@Service
public class PlatformBrandingService {

    private static final short SINGLETON_ID = PlatformBranding.SINGLETON_ID;
    private static final long MAX_LOGO_BYTES = 1024L * 1024L;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp");

    private final PlatformBrandingRepository repository;
    private final AuditService auditService;
    private final Path artifactsRoot;

    /**
     * Constructs the branding service.
     *
     * @param repository branding repository
     * @param auditService audit writer
     * @param artifactsRoot artifacts root directory
     */
    public PlatformBrandingService(PlatformBrandingRepository repository,
            AuditService auditService,
            @Value("${einvoice.artifacts.root:/var/lib/einvoice/artifacts}")
                    String artifactsRoot) {
        this.repository = repository;
        this.auditService = auditService;
        this.artifactsRoot = Path.of(artifactsRoot).toAbsolutePath()
                .normalize();
    }

    /**
     * Loads the current logo from disk.
     *
     * @return optional logo resource
     * @throws IOException when the stored file cannot be read
     */
    @Transactional(readOnly = true)
    public Optional<LogoResource> getLogo() throws IOException {
        PlatformBranding branding = repository.findById(SINGLETON_ID)
                .orElseGet(PlatformBranding::new);
        if (branding.getLogoPath() == null || branding.getLogoMime() == null) {
            return Optional.empty();
        }

        Path logoPath = Path.of(branding.getLogoPath()).toAbsolutePath()
                .normalize();
        if (!logoPath.startsWith(artifactsRoot) || !Files.isRegularFile(logoPath)) {
            return Optional.empty();
        }
        return Optional.of(new LogoResource(
                Files.readAllBytes(logoPath), branding.getLogoMime()));
    }

    /**
     * Validates and stores a replacement platform logo.
     *
     * @param file multipart logo file
     * @return updated branding state
     * @throws IOException when the file cannot be stored
     */
    @Transactional
    public PlatformBrandingResponse uploadLogo(MultipartFile file)
            throws IOException {
        byte[] content = validate(file);
        String mimeType = file.getContentType();
        Path directory = artifactsRoot.resolve("platform-branding");
        Files.createDirectories(directory);

        Path target = directory.resolve("platform-logo-" + UUID.randomUUID()
                + extensionFor(mimeType));
        Path temp = Files.createTempFile(directory, "platform-logo-", ".tmp");
        Files.write(temp, content);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);

        PlatformBranding branding = getBranding();
        final String oldPath = branding.getLogoPath();
        branding.setLogoPath(target.toString());
        branding.setLogoMime(mimeType);
        branding.setUpdatedBy(TenantContext.getUserId());
        branding.setUpdatedAt(OffsetDateTime.now());
        repository.save(branding);

        deleteOldLogo(oldPath, target);
        auditService.record(null, "UPLOAD_LOGO", "PlatformBranding",
                String.valueOf(SINGLETON_ID), null,
                Map.of("logoMime", mimeType, "size", content.length));
        return toResponse(branding);
    }

    /**
     * Clears the configured platform logo.
     *
     * @throws IOException when the old file cannot be removed
     */
    @Transactional
    public void deleteLogo() throws IOException {
        PlatformBranding branding = getBranding();
        final String oldPath = branding.getLogoPath();
        branding.setLogoPath(null);
        branding.setLogoMime(null);
        branding.setUpdatedBy(TenantContext.getUserId());
        branding.setUpdatedAt(OffsetDateTime.now());
        repository.save(branding);

        deleteOldLogo(oldPath, null);
        auditService.record(null, "DELETE_LOGO", "PlatformBranding",
                String.valueOf(SINGLETON_ID), null, Map.of());
    }

    private PlatformBranding getBranding() {
        return repository.findById(SINGLETON_ID)
                .orElseGet(() -> repository.save(new PlatformBranding()));
    }

    private byte[] validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw badRequest("Logo file is required");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw badRequest("Logo must be 1 MB or smaller");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw badRequest("Logo must be PNG, JPEG, or WebP");
        }

        byte[] content = file.getBytes();
        if (!matchesMagicBytes(content, contentType)) {
            throw badRequest("Logo content does not match its MIME type");
        }
        return content;
    }

    private boolean matchesMagicBytes(byte[] content, String mimeType) {
        return switch (mimeType) {
          case "image/png" -> content.length >= 8
                  && (content[0] & 0xff) == 0x89
                  && content[1] == 0x50
                  && content[2] == 0x4e
                  && content[3] == 0x47
                  && content[4] == 0x0d
                  && content[5] == 0x0a
                  && content[6] == 0x1a
                  && content[7] == 0x0a;
          case "image/jpeg" -> content.length >= 3
                  && (content[0] & 0xff) == 0xff
                  && (content[1] & 0xff) == 0xd8
                  && (content[2] & 0xff) == 0xff;
          case "image/webp" -> content.length >= 12
                  && content[0] == 0x52
                  && content[1] == 0x49
                  && content[2] == 0x46
                  && content[3] == 0x46
                  && content[8] == 0x57
                  && content[9] == 0x45
                  && content[10] == 0x42
                  && content[11] == 0x50;
          default -> false;
        };
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
          case "image/png" -> ".png";
          case "image/jpeg" -> ".jpg";
          case "image/webp" -> ".webp";
          default -> "";
        };
    }

    private void deleteOldLogo(String oldPath, Path newPath) throws IOException {
        if (oldPath == null) {
            return;
        }
        Path path = Path.of(oldPath).toAbsolutePath().normalize();
        if (!path.startsWith(artifactsRoot) || path.equals(newPath)) {
            return;
        }
        Files.deleteIfExists(path);
    }

    private PlatformBrandingResponse toResponse(PlatformBranding branding) {
        return new PlatformBrandingResponse(
                branding.getLogoPath() != null,
                branding.getLogoMime(),
                branding.getUpdatedAt());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Logo bytes with their persisted content type. */
    public record LogoResource(byte[] content, String mimeType) {
    }
}
