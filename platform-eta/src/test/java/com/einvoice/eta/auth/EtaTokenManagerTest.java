package com.einvoice.eta.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.service.CryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class EtaTokenManagerTest {

    private EtaTokenManager tokenManager;
    private CryptoService cryptoService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cryptoService = new CryptoService() {
            @Override
            public byte[] encrypt(byte[] plaintext) {
                return plaintext;
            }

            @Override
            public byte[] decrypt(byte[] encrypted) {
                return encrypted;
            }
        };
    }

    @Test
    void refreshToken_cachesAndReturnsToken() {
        String tokenResponse = "{\"access_token\":\"test-token-123\",\"expires_in\":3600}";
        RestClient restClient = mockRestClient(tokenResponse);

        tokenManager = new EtaTokenManager(
                RestClient.builder(), objectMapper, cryptoService) {
        };
        tokenManager = new EtaTokenManagerWithMock(restClient, objectMapper, cryptoService);

        AuthorityConfig config = buildConfig("client1", "secret1");
        String token = tokenManager.refreshToken(config);

        assertEquals("test-token-123", token);
    }

    @Test
    void getToken_returnsCachedTokenIfNotExpired() {
        String tokenResponse = "{\"access_token\":\"cached-token\",\"expires_in\":3600}";
        RestClient restClient = mockRestClient(tokenResponse);
        tokenManager = new EtaTokenManagerWithMock(restClient, objectMapper, cryptoService);

        AuthorityConfig config = buildConfig("client1", "secret1");
        String first = tokenManager.getToken(config);
        String second = tokenManager.getToken(config);

        assertEquals("cached-token", first);
        assertEquals("cached-token", second);
    }

    @Test
    void invalidateToken_clearsCache() {
        String tokenResponse = "{\"access_token\":\"to-invalidate\",\"expires_in\":3600}";
        RestClient restClient = mockRestClient(tokenResponse);
        tokenManager = new EtaTokenManagerWithMock(restClient, objectMapper, cryptoService);

        AuthorityConfig config = buildConfig("client1", "secret1");
        String first = tokenManager.getToken(config);
        assertEquals("to-invalidate", first);

        tokenManager.invalidateToken(config.getBranch().getId(), config.getEnvironment());

        String newTokenResponse = "{\"access_token\":\"refreshed-token\",\"expires_in\":3600}";
        RestClient newRestClient = mockRestClient(newTokenResponse);
        tokenManager = new EtaTokenManagerWithMock(newRestClient, objectMapper, cryptoService);

        String refreshed = tokenManager.refreshToken(config);
        assertEquals("refreshed-token", refreshed);
    }

    @Test
    void refreshToken_throwsOnMissingCredentials() {
        tokenManager = new EtaTokenManager(
                RestClient.builder(), objectMapper, cryptoService);

        AuthorityConfig config = AuthorityConfig.builder()
                .branch(Branch.builder().id(1L).build())
                .environment(Environment.ETA_PREPRODUCTION)
                .build();

        assertThrows(IllegalStateException.class,
                () -> tokenManager.refreshToken(config));
    }

    @Test
    void resolveBaseUrl_returnsCorrectUrlForPreprod() {
        tokenManager = new EtaTokenManager(
                RestClient.builder(), objectMapper, cryptoService);

        String url = invokeResolveBaseUrl(tokenManager, Environment.ETA_PREPRODUCTION);
        assertTrue(url.contains("preprod.eta.gov.eg"));
        assertTrue(!url.endsWith("/connect"));
    }

    @Test
    void resolveBaseUrl_returnsCorrectUrlForProduction() {
        tokenManager = new EtaTokenManager(
                RestClient.builder(), objectMapper, cryptoService);

        String url = invokeResolveBaseUrl(tokenManager, Environment.ETA_PRODUCTION);
        assertTrue(url.contains("id.eta.gov.eg"));
        assertTrue(!url.endsWith("/connect"));
    }

    @Test
    void getToken_refreshesWhenCachedTokenIsInsideRefreshBuffer() {
        // expires_in=30 is less than REFRESH_BUFFER_SECONDS=60,
        // so the cached token is treated as expired on arrival.
        String shortLived = "{\"access_token\":\"short-lived\",\"expires_in\":30}";
        String refreshed = "{\"access_token\":\"refreshed\",\"expires_in\":3600}";
        RestClient restClient = mockRestClientSequence(shortLived, refreshed);
        tokenManager = new EtaTokenManagerWithMock(restClient, objectMapper, cryptoService);

        AuthorityConfig config = buildConfig("client1", "secret1");
        String first = tokenManager.getToken(config);
        String second = tokenManager.getToken(config);

        assertEquals("short-lived", first);
        assertEquals("refreshed", second,
                "Second call must refresh because the cached token is inside the buffer window");
    }

    @Test
    void getToken_isSafeForConcurrentAccess() throws InterruptedException {
        String response = "{\"access_token\":\"concurrent-token\",\"expires_in\":3600}";
        RestClient restClient = mockRestClient(response);
        tokenManager = new EtaTokenManagerWithMock(restClient, objectMapper, cryptoService);

        AuthorityConfig config = buildConfig("client1", "secret1");

        int threadCount = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        results.add(tokenManager.getToken(config));
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS),
                    "Threads did not finish within timeout");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(errors.isEmpty(), "No errors expected; got: " + errors);
        assertEquals(threadCount, results.size());
        for (String r : results) {
            assertEquals("concurrent-token", r);
        }
    }

    @SuppressWarnings("unchecked")
    private RestClient mockRestClient(String responseBody) {
        RestClient mockClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any())).thenReturn(bodySpec);
        when(bodySpec.accept(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);

        return mockClient;
    }

    @SuppressWarnings("unchecked")
    private RestClient mockRestClientSequence(String... responseBodies) {
        RestClient mockClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(mockClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any())).thenReturn(bodySpec);
        when(bodySpec.accept(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        OngoingStubbing<String> stub = when(responseSpec.body(String.class));
        for (String body : responseBodies) {
            stub = stub.thenReturn(body);
        }

        return mockClient;
    }

    private AuthorityConfig buildConfig(String clientId, String clientSecret) {
        return AuthorityConfig.builder()
                .branch(Branch.builder().id(1L).build())
                .environment(Environment.ETA_PREPRODUCTION)
                .csidEncrypted(clientId.getBytes(StandardCharsets.UTF_8))
                .credentialsEncrypted(clientSecret.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private String invokeResolveBaseUrl(EtaTokenManager mgr, Environment env) {
        try {
            var method = EtaTokenManager.class.getDeclaredMethod(
                    "resolveBaseUrl", Environment.class);
            method.setAccessible(true);
            return (String) method.invoke(mgr, env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class EtaTokenManagerWithMock extends EtaTokenManager {
        private final RestClient mockRestClient;

        EtaTokenManagerWithMock(RestClient mockRestClient,
                ObjectMapper objectMapper, CryptoService cryptoService) {
            super(RestClient.builder(), objectMapper, cryptoService);
            this.mockRestClient = mockRestClient;
        }

        @Override
        public String refreshToken(AuthorityConfig config) {
            try {
                var field = EtaTokenManager.class.getDeclaredField("restClient");
                field.setAccessible(true);
                field.set(this, mockRestClient);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.refreshToken(config);
        }
    }
}
