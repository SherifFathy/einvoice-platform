package com.einvoice.api.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.einvoice.api.session.SessionContextAssembler;
import com.einvoice.api.session.dto.SessionContextResponse;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.security.permission.PermissionService;
import com.einvoice.security.tenant.TenantContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class Wave7PermissionsAssemblyTest {

    @Mock private UserCompanyTransactionRoleRepository uctrRepo;
    @Mock private CompanyRepository companyRepository;
    @Mock private PermissionService permissionService;

    private SessionContextAssembler assembler;

    private UUID userId;
    private UUID companyId;
    private Short envId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        assembler = new SessionContextAssembler(uctrRepo, companyRepository, permissionService);
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        envId = 2;
    }

    @Test
    void accountantUnderEtaPreProd_includesInvoiceAndReceiptPermissions() {
        Company company = Company.builder()
                .id(companyId).nameEn("Test Co").nameAr("اختبار").isActive(true).build();

        UserCompanyTransactionRole assignment = UserCompanyTransactionRole.builder()
                .company(company).authorityEnvironmentId(envId).build();

        when(uctrRepo.findByUserIdAndAuthorityEnvironmentIdAndIsActiveTrue(userId, envId))
                .thenReturn(List.of(assignment));

        when(permissionService.permissionsFor(eq(userId), eq(companyId), eq(envId), eq("INVOICE")))
                .thenReturn(Set.of("VIEW", "CREATE", "SUBMIT", "REFRESH"));
        when(permissionService.permissionsFor(eq(userId), eq(companyId), eq(envId), eq("RECEIPT")))
                .thenReturn(Set.of("VIEW", "CREATE", "SUBMIT", "REFRESH"));
        when(permissionService.permissionsFor(eq(userId), eq(companyId), eq(envId), eq("CUSTOMERS")))
                .thenReturn(Set.of("VIEW", "CREATE"));
        when(permissionService.permissionsFor(eq(userId), eq(companyId), eq(envId), eq("ITEMS")))
                .thenReturn(Set.of("VIEW", "CREATE"));
        when(permissionService.permissionsFor(eq(userId), eq(companyId), eq(envId), eq("CONFIG")))
                .thenReturn(Set.of());

        TenantContext.Holder holder = new TenantContext.Holder(
                userId, companyId, envId, "ETA", "PREPROD",
                TenantContext.Mode.OPERATIONAL_MODE,
                false, System.currentTimeMillis(), "test-jti");

        SessionContextResponse response = assembler.assemble(holder);

        assertNotNull(response.companies());
        assertEquals(1, response.companies().size());

        SessionContextResponse.CompanyContext cc = response.companies().get(0);
        assertEquals(companyId, cc.companyId());

        SessionContextResponse.ModulePermissions invoicePerms = cc.modules().get("invoice");
        assertNotNull(invoicePerms);
        assertTrue(invoicePerms.visible());
        assertTrue(invoicePerms.permissions().view());
        assertTrue(invoicePerms.permissions().create());
        assertTrue(invoicePerms.permissions().submit());
        assertTrue(invoicePerms.permissions().refresh());

        SessionContextResponse.ModulePermissions receiptPerms = cc.modules().get("receipt");
        assertNotNull(receiptPerms);
        assertTrue(receiptPerms.visible());
        assertTrue(receiptPerms.permissions().view());
        assertTrue(receiptPerms.permissions().create());
        assertTrue(receiptPerms.permissions().submit());
        assertTrue(receiptPerms.permissions().refresh());
    }
}
