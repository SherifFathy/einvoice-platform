package com.einvoice.api.session;

import com.einvoice.api.session.dto.SessionContextResponse;
import com.einvoice.api.session.dto.SessionContextResponse.CompanyContext;
import com.einvoice.api.session.dto.SessionContextResponse.LoginContext;
import com.einvoice.api.session.dto.SessionContextResponse.ModulePermissions;
import com.einvoice.api.session.dto.SessionContextResponse.Permissions;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.security.permission.PermissionService;
import com.einvoice.security.tenant.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Assembles the full session context response from tenant and permission data. */
@Component
public class SessionContextAssembler {

    private static final List<String> ETA_MODULES = List.of(
            "invoice", "receipt", "customers", "items", "configuration");
    private static final List<String> ZATCA_MODULES = List.of(
            "standard", "simplified", "customers", "items", "configuration");

    private static final Map<String, String> ETA_TX_TYPE_MAP = Map.of(
            "invoice", "INVOICE",
            "receipt", "RECEIPT",
            "customers", "CUSTOMERS",
            "items", "ITEMS",
            "configuration", "CONFIG");

    private static final Map<String, String> ZATCA_TX_TYPE_MAP = Map.of(
            "standard", "STANDARD",
            "simplified", "SIMPLIFIED",
            "customers", "CUSTOMERS",
            "items", "ITEMS",
            "configuration", "CONFIG");

    private static final Set<String> DOC_MODULES = Set.of("invoice", "receipt", "standard", "simplified");

    private final UserCompanyTransactionRoleRepository uctrRepo;
    private final CompanyRepository companyRepository;
    private final PermissionService permissionService;

    /**
     * Constructs the assembler with required repositories and permission service.
     *
     * @param uctrRepo the UCTR repository
     * @param companyRepository the company repository
     * @param permissionService the permission service
     */
    public SessionContextAssembler(
            UserCompanyTransactionRoleRepository uctrRepo,
            CompanyRepository companyRepository,
            PermissionService permissionService) {
        this.uctrRepo = uctrRepo;
        this.companyRepository = companyRepository;
        this.permissionService = permissionService;
    }

    /**
     * Builds the session context response for the given tenant holder.
     *
     * @param holder the tenant context holder
     * @return the session context response
     */
    public SessionContextResponse assemble(TenantContext.Holder holder) {
        LoginContext loginContext = new LoginContext(
                holder.authority(), holder.environment(), holder.authorityEnvironmentId());

        if (holder.mode() == TenantContext.Mode.ADMIN_MODE) {
            return new SessionContextResponse(
                    holder.userId(), holder.isSuperUser(),
                    holder.mode().name(), holder.companyId(), loginContext, List.of());
        }

        String authority = holder.authority();
        List<String> moduleKeys = "ETA".equals(authority) ? ETA_MODULES : ZATCA_MODULES;
        Map<String, String> txTypeMap = "ETA".equals(authority) ? ETA_TX_TYPE_MAP : ZATCA_TX_TYPE_MAP;

        List<CompanyContext> companies = buildCompanies(holder, moduleKeys, txTypeMap);

        return new SessionContextResponse(
                holder.userId(), holder.isSuperUser(),
                holder.mode().name(), holder.companyId(), loginContext, companies);
    }

    private List<CompanyContext> buildCompanies(TenantContext.Holder holder,
            List<String> moduleKeys, Map<String, String> txTypeMap) {

        if (holder.isSuperUser() && holder.mode() == TenantContext.Mode.OPERATIONAL_MODE) {
            return buildSuperUserCompanies(holder, moduleKeys);
        }

        return buildRegularUserCompanies(holder, moduleKeys, txTypeMap);
    }

    private List<CompanyContext> buildSuperUserCompanies(TenantContext.Holder holder,
            List<String> moduleKeys) {
        List<UUID> companyIds = uctrRepo
                .findDistinctCompanyIdsByAuthorityEnvironmentIdAndIsActiveTrue(
                        holder.authorityEnvironmentId());

        List<Company> companies = companyRepository.findAllById(companyIds).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .toList();

        List<CompanyContext> result = new ArrayList<>();
        for (Company company : companies) {
            Map<String, ModulePermissions> modules = new LinkedHashMap<>();
            for (String moduleKey : moduleKeys) {
                modules.put(moduleKey, buildAllTrueModule(moduleKey));
            }
            result.add(new CompanyContext(
                    company.getId(), company.getNameEn(), company.getNameAr(),
                    true, modules));
        }
        return result;
    }

    private List<CompanyContext> buildRegularUserCompanies(TenantContext.Holder holder,
            List<String> moduleKeys, Map<String, String> txTypeMap) {
        List<UserCompanyTransactionRole> assignments = uctrRepo
                .findByUserIdAndAuthorityEnvironmentIdAndIsActiveTrue(
                        holder.userId(), holder.authorityEnvironmentId());

        Map<UUID, List<UserCompanyTransactionRole>> byCompany = assignments.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCompany().getId(), LinkedHashMap::new, Collectors.toList()));

        List<CompanyContext> result = new ArrayList<>();
        for (var entry : byCompany.entrySet()) {
            UUID companyId = entry.getKey();
            List<UserCompanyTransactionRole> companyAssignments = entry.getValue();
            Company company = companyAssignments.get(0).getCompany();

            Map<String, ModulePermissions> modules = new LinkedHashMap<>();
            for (String moduleKey : moduleKeys) {
                String txType = txTypeMap.get(moduleKey);
                Set<String> perms = permissionService.permissionsFor(
                        holder.userId(), companyId, holder.authorityEnvironmentId(), txType);
                modules.put(moduleKey, buildModule(moduleKey, perms));
            }

            result.add(new CompanyContext(
                    company.getId(), company.getNameEn(), company.getNameAr(),
                    Boolean.TRUE.equals(company.getIsActive()), modules));
        }
        return result;
    }

    private ModulePermissions buildAllTrueModule(String moduleKey) {
        boolean isDocModule = DOC_MODULES.contains(moduleKey);
        return new ModulePermissions(true, new Permissions(true, true, true, true,
                isDocModule, isDocModule, true, isDocModule));
    }

    private ModulePermissions buildModule(String moduleKey, Set<String> perms) {
        boolean visible = perms.contains("VIEW");
        boolean isDocModule = DOC_MODULES.contains(moduleKey);
        return new ModulePermissions(visible, new Permissions(
                perms.contains("VIEW"),
                perms.contains("CREATE"),
                perms.contains("EDIT"),
                perms.contains("DELETE"),
                isDocModule && perms.contains("CANCEL"),
                isDocModule && perms.contains("TRANSFER"),
                perms.contains("REFRESH"),
                isDocModule && perms.contains("SUBMIT")));
    }
}
