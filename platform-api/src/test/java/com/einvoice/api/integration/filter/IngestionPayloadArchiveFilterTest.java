package com.einvoice.api.integration.filter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.api.integration.service.InboundPayloadArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class IngestionPayloadArchiveFilterTest {

    @Mock
    private InboundPayloadArchiveService archiveService;

    @Mock
    private FilterChain filterChain;

    private IngestionPayloadArchiveFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IngestionPayloadArchiveFilter(archiveService, new ObjectMapper());
    }

    @Test
    void unauthenticatedRequest_createsArchiveRow() throws Exception {
        UUID archiveId = UUID.randomUUID();
        when(archiveService.archive(eq("/api/integration/v1/eta/receipts"), any(byte[].class)))
                .thenReturn(archiveId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/integration/v1/eta/receipts");
        request.setContentType("application/json");
        request.setContent("{\"companyRegistrationNumber\":\"100200300\"}".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(archiveService).archive(eq("/api/integration/v1/eta/receipts"), any(byte[].class));
        verify(archiveService).patchOutcome(eq(archiveId), eq(200), isNull(), isNull());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void nonIntegrationPath_isNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/health");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void malformedJson_stillArchivesAndContinues() throws Exception {
        UUID archiveId = UUID.randomUUID();
        when(archiveService.archive(any(), any(byte[].class))).thenReturn(archiveId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/integration/v1/eta/receipts");
        request.setContentType("application/json");
        request.setContent("this is not json {{{".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(archiveService).archive(eq("/api/integration/v1/eta/receipts"), any(byte[].class));
        verify(filterChain).doFilter(any(), any());
    }
}
