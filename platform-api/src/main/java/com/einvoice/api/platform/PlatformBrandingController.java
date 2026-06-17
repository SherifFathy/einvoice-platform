package com.einvoice.api.platform;

import com.einvoice.api.platform.PlatformBrandingService.LogoResource;
import com.einvoice.api.platform.dto.PlatformBrandingResponse;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Platform-wide branding endpoints. */
@RestController
@RequestMapping("/api/platform/branding")
public class PlatformBrandingController {

    private final PlatformBrandingService service;

    /**
     * Constructs the controller.
     *
     * @param service platform branding service
     */
    public PlatformBrandingController(PlatformBrandingService service) {
        this.service = service;
    }

    /**
     * Returns the configured platform logo bytes.
     *
     * @return logo response
     * @throws IOException when the logo file cannot be read
     */
    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() throws IOException {
        LogoResource logo = service.getLogo()
                .orElseThrow(() -> new org.springframework.web.server
                        .ResponseStatusException(HttpStatus.NOT_FOUND,
                                "No platform logo configured"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.parseMediaType(logo.mimeType()))
                .body(logo.content());
    }

    /**
     * Uploads or replaces the platform logo.
     *
     * @param file multipart logo file
     * @return updated branding state
     * @throws IOException when the logo cannot be stored
     */
    @PostMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SUPER_USER')")
    public PlatformBrandingResponse uploadLogo(
            @RequestParam("file") MultipartFile file) throws IOException {
        return service.uploadLogo(file);
    }

    /**
     * Removes the configured platform logo.
     *
     * @return no-content response
     * @throws IOException when the stored logo cannot be removed
     */
    @DeleteMapping("/logo")
    @PreAuthorize("hasAuthority('SUPER_USER')")
    public ResponseEntity<Void> deleteLogo() throws IOException {
        service.deleteLogo();
        return ResponseEntity.noContent().build();
    }
}
