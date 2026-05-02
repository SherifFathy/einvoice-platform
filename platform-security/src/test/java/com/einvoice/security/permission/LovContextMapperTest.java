package com.einvoice.security.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LovContextMapperTest {

    @Test
    void toAuthorityEnvironment_zatcaSandbox() {
        assertEquals("ZATCA_SANDBOX",
                LovContextMapper.toAuthorityEnvironment("ZATCA", "SANDBOX"));
    }

    @Test
    void toAuthorityEnvironment_zatcaSimulation() {
        assertEquals("ZATCA_SIMULATION",
                LovContextMapper.toAuthorityEnvironment("ZATCA", "SIMULATION"));
    }

    @Test
    void toAuthorityEnvironment_zatcaProduction() {
        assertEquals("ZATCA_PRODUCTION",
                LovContextMapper.toAuthorityEnvironment("ZATCA", "PRODUCTION"));
    }

    @Test
    void toAuthorityEnvironment_etaPreprod() {
        assertEquals("ETA_PREPRODUCTION",
                LovContextMapper.toAuthorityEnvironment("ETA", "PREPROD"));
    }

    @Test
    void toAuthorityEnvironment_etaProduction() {
        assertEquals("ETA_PRODUCTION",
                LovContextMapper.toAuthorityEnvironment("ETA", "PRODUCTION"));
    }

    @Test
    void toAuthorityEnvironment_zatcaPreprod_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LovContextMapper.toAuthorityEnvironment("ZATCA", "PREPROD"));
    }

    @Test
    void toAuthorityEnvironment_etaSimulation_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LovContextMapper.toAuthorityEnvironment("ETA", "SIMULATION"));
    }
}
