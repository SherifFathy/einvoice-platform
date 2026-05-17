package com.einvoice.core.authority;

import java.util.UUID;

/** Credentials for authority authentication and submission. */
public record AuthorityCredentials(
        UUID companyId,
        String clientId,
        String clientSecret,
        String tokenUrl,
        String submissionUrl
) {}
