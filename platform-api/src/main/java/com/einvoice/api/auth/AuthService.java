package com.einvoice.api.auth;

import com.einvoice.api.auth.dto.CompaniesRequest;
import com.einvoice.api.auth.dto.CompaniesResponse;
import com.einvoice.api.auth.dto.EnvironmentsRequest;
import com.einvoice.api.auth.dto.EnvironmentsResponse;
import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.api.auth.dto.LoginResponse;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.rbac.AuthorityEnvironment;
import com.einvoice.core.domain.rbac.UserCompanyTransactionRole;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.error.BadCredentialsException;
import com.einvoice.core.error.CompanyContextRequiredException;
import com.einvoice.core.error.InactiveCompanyException;
import com.einvoice.core.error.InvalidAuthorityEnvironmentException;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.AuthorityEnvironmentRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service handling authentication, environment lookup, company listing, and login. */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AuthorityEnvironmentRepository authEnvRepo;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final UserCompanyTransactionRoleRepository uctrRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Constructs an AuthService with the required repositories and utilities.
     *
     * @param authEnvRepo the authority environment repository
     * @param userRepository the user repository
     * @param companyRepository the company repository
     * @param uctrRepo the UCTR repository
     * @param passwordEncoder the password encoder
     * @param jwtTokenProvider the JWT token provider
     */
    public AuthService(AuthorityEnvironmentRepository authEnvRepo,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            UserCompanyTransactionRoleRepository uctrRepo,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.authEnvRepo = authEnvRepo;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.uctrRepo = uctrRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Lists active authority environments filtered by the requested authority.
     *
     * @param request the environments request
     * @return the environments response
     */
    public EnvironmentsResponse listEnvironments(EnvironmentsRequest request) {
        List<AuthorityEnvironment> envs = authEnvRepo.findByIsActiveTrueOrderById();
        List<EnvironmentsResponse.EnvironmentEntry> filtered = envs.stream()
                .filter(e -> e.getAuthority().equals(request.authority()))
                .map(e -> new EnvironmentsResponse.EnvironmentEntry(
                        e.getId(), e.getAuthority(), e.getEnvironment(), e.getLabel(), e.getIsActive()))
                .toList();
        return new EnvironmentsResponse(filtered);
    }

    /**
     * Lists companies accessible to the user for the given authority and environment.
     *
     * @param request the companies request
     * @return the companies response
     */
    public CompaniesResponse listCompanies(CompaniesRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        AuthorityEnvironment authEnv = authEnvRepo
                .findByAuthorityAndEnvironmentAndIsActiveTrue(request.authority(), request.environment())
                .orElse(null);

        if (user == null || authEnv == null || !Boolean.TRUE.equals(user.getIsActive())) {
            return new CompaniesResponse(false, List.of());
        }

        boolean isSuperUser = Boolean.TRUE.equals(user.getIsSuperUser());

        if (isSuperUser) {
            List<CompaniesResponse.CompanyEntry> companies = companyRepository.findByIsActiveTrue().stream()
                    .map(c -> new CompaniesResponse.CompanyEntry(
                            c.getId(), c.getNameEn(), c.getNameAr(), c.getTaxNumber(), c.getIsActive()))
                    .toList();
            return new CompaniesResponse(true, companies);
        }

        List<UserCompanyTransactionRole> assignments = uctrRepo
                .findByUserIdAndAuthorityEnvironmentIdAndIsActiveTrue(user.getId(), authEnv.getId());

        LinkedHashMap<UUID, Company> deduped = assignments.stream()
                .collect(LinkedHashMap::new,
                        (map, a) -> map.putIfAbsent(a.getCompany().getId(), a.getCompany()),
                        LinkedHashMap::putAll);

        List<CompaniesResponse.CompanyEntry> companies = deduped.values().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .map(c -> new CompaniesResponse.CompanyEntry(
                        c.getId(), c.getNameEn(), c.getNameAr(), c.getTaxNumber(), c.getIsActive()))
                .toList();

        return new CompaniesResponse(false, companies);
    }

    /**
     * Authenticates the user and returns a JWT token with session metadata.
     *
     * @param request the login request
     * @return the login response with JWT token
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        AuthorityEnvironment authEnv = authEnvRepo
                .findByAuthorityAndEnvironmentAndIsActiveTrue(request.authority(), request.environment())
                .orElseThrow(() -> new InvalidAuthorityEnvironmentException(
                        "Invalid authority and environment combination"));

        TenantContext.Mode mode;
        UUID companyId = request.companyId();

        if (user.getIsSuperUser()) {
            if (companyId == null) {
                mode = TenantContext.Mode.ADMIN_MODE;
            } else {
                mode = TenantContext.Mode.OPERATIONAL_MODE;
                Company company = companyRepository.findById(companyId)
                        .orElseThrow(() -> new InactiveCompanyException(
                                "Selected company is not active"));
                if (!Boolean.TRUE.equals(company.getIsActive())) {
                    throw new InactiveCompanyException("Selected company is not active");
                }
            }
        } else {
            if (companyId == null) {
                throw new CompanyContextRequiredException(
                        "A company selection is required for non-administrator users");
            }

            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new InactiveCompanyException(
                            "Selected company is not active"));
            if (!Boolean.TRUE.equals(company.getIsActive())) {
                throw new InactiveCompanyException("Selected company is not active");
            }

            List<UserCompanyTransactionRole> assignments = uctrRepo
                    .findByUserIdAndCompanyIdAndAuthorityEnvironmentIdAndIsActiveTrue(
                            user.getId(), companyId, authEnv.getId());
            if (assignments.isEmpty()) {
                throw new UnauthorizedContextException(
                        "You have no active assignments for the selected authority and environment");
            }
            mode = TenantContext.Mode.OPERATIONAL_MODE;
        }

        String token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), user.getIsSuperUser(),
                request.authority(), request.environment(), authEnv.getId(),
                companyId, mode);

        return new LoginResponse(token, "Bearer", jwtTokenProvider.getTtlSeconds(), mode.name());
    }
}
