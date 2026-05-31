package com.einvoice.api.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.einvoice.core.domain.shared.AuditLog;
import com.einvoice.core.repository.shared.AuditLogRepository;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    @Captor
    private ArgumentCaptor<AuditLog> logCaptor;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository);
    }

    @Test
    void recordWritesOneRowWithCorrectAction() {
        service.record("CREATE_INVOICE", "ETA_INVOICE", "doc-123", null, Map.of("state", "DRAFT"));
        verify(repository).save(logCaptor.capture());
        assertEquals("CREATE_INVOICE", logCaptor.getValue().getAction());
        assertEquals("ETA_INVOICE", logCaptor.getValue().getEntityType());
        assertEquals("doc-123", logCaptor.getValue().getEntityId());
    }

    @Test
    void noPublicUpdateOrDeleteMethods() {
        for (Method m : AuditService.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            boolean isUpdateOrDelete = name.startsWith("update") || name.startsWith("delete");
            assertTrue(!isUpdateOrDelete,
                    "AuditService must not expose update/delete methods, found: " + m.getName());
        }
    }
}
