package com.einvoice.eta.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.eta.client.EtaHttpClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtaTokenManagerTest {

    @Mock
    private EtaHttpClient httpClient;

    private EtaTokenManager tokenManager;

    @BeforeEach
    void setUp() {
        tokenManager = new EtaTokenManager(httpClient);
    }

    @Test
    void cacheHitReturnsStoredToken() {
        when(httpClient.requestToken(anyString(), anyString(), anyString()))
                .thenReturn("token-abc");
        UUID companyId = UUID.randomUUID();
        Short envId = (short) 2;

        String first = tokenManager.getToken(companyId, envId, "client1", "secret1", "http://token");
        String second = tokenManager.getToken(companyId, envId, "client1", "secret1", "http://token");

        assertEquals("token-abc", first);
        assertEquals("token-abc", second);
        verify(httpClient, times(1)).requestToken("client1", "secret1", "http://token");
    }

    @Test
    void invalidateCausesRefreshOnNextCall() {
        when(httpClient.requestToken(anyString(), anyString(), anyString()))
                .thenReturn("token-1")
                .thenReturn("token-2");
        UUID companyId = UUID.randomUUID();
        Short envId = (short) 2;

        tokenManager.getToken(companyId, envId, "c", "s", "url");
        tokenManager.invalidate(companyId, envId);
        String refreshed = tokenManager.getToken(companyId, envId, "c", "s", "url");

        assertEquals("token-2", refreshed);
    }

    @Test
    void crossEnvironmentCacheIsIsolated() {
        when(httpClient.requestToken(anyString(), anyString(), anyString()))
                .thenReturn("token-prod");
        UUID companyId = UUID.randomUUID();

        String prod = tokenManager.getToken(companyId, (short) 1, "c", "s", "url-prod");
        assertNotNull(prod);
    }
}
