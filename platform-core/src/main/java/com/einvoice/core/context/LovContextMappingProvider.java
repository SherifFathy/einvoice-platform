package com.einvoice.core.context;

/**
 * SPI for mapping LOV context components to the authority_configs environment enum value.
 * Implemented by platform-security's LovContextMapper to decouple
 * platform-core from platform-security.
 */
public interface LovContextMappingProvider {

    /**
     * Maps an authority and sub-environment pair to the corresponding
     * authority_configs.environment enum value.
     *
     * @param authority the authority (ZATCA or ETA)
     * @param subEnv the sub-environment (SANDBOX, SIMULATION, PRODUCTION, PREPROD)
     * @return the authority_configs.environment enum name
     * @throws IllegalArgumentException if the combination is invalid
     */
    String toAuthorityEnvironment(String authority, String subEnv);
}
