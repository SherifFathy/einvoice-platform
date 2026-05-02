package com.einvoice.security.permission;

/** Maps authority and sub-environment identifiers to LOV context environment strings. */
public final class LovContextMapper {

    private LovContextMapper() {
    }

    /**
     * Converts an authority and sub-environment pair to a composite environment key.
     *
     * @param authority the authority identifier (e.g. ZATCA, ETA)
     * @param subEnv the sub-environment (e.g. SANDBOX, PRODUCTION)
     * @return the composite environment key used for LOV context resolution
     */
    public static String toAuthorityEnvironment(String authority, String subEnv) {
        if ("ZATCA".equals(authority)) {
            return switch (subEnv) {
              case "SANDBOX" -> "ZATCA_SANDBOX";
              case "SIMULATION" -> "ZATCA_SIMULATION";
              case "PRODUCTION" -> "ZATCA_PRODUCTION";
              default -> throw new IllegalArgumentException(
                      "Unknown subEnv for ZATCA: " + subEnv);
            };
        }
        if ("ETA".equals(authority)) {
            return switch (subEnv) {
              case "PREPROD" -> "ETA_PREPRODUCTION";
              case "PRODUCTION" -> "ETA_PRODUCTION";
              default -> throw new IllegalArgumentException(
                      "Unknown subEnv for ETA: " + subEnv);
            };
        }
        throw new IllegalArgumentException(
                "Unknown authority: " + authority);
    }
}
