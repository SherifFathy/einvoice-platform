package com.einvoice.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuditLogReadOnlyApiTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    private static final Set<String> IMMUTABLE_PACKAGES = Set.of(
            "com.einvoice.api.audit",
            "com.einvoice.api.eta.artifact");

    private static final Set<String> FORBIDDEN_METHODS = Set.of(
            "PUT", "PATCH", "DELETE");

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void noPutPatchDeleteEndpointsOnImmutableResources() {
        List<String> violations = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            String declaringPackage =
                    method.getBeanType().getPackageName();

            boolean immutable = IMMUTABLE_PACKAGES.stream()
                    .anyMatch(declaringPackage::startsWith);
            if (!immutable) {
                return;
            }

            Set<String> httpMethods = info.getMethodsCondition()
                    .getMethods()
                    .stream()
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.toSet());

            boolean hasForbidden = httpMethods.stream()
                    .anyMatch(FORBIDDEN_METHODS::contains);

            if (!hasForbidden) {
                return;
            }

            String path = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatterns()
                            .toString()
                    : info.getPatternsCondition() != null
                            ? info.getPatternsCondition()
                                    .getPatterns().toString()
                            : "?";

            violations.add(httpMethods + " " + path
                    + " -> "
                    + method.getBeanType().getSimpleName()
                    + "." + method.getMethod().getName());
        });

        assertThat(violations)
                .as("No PUT/PATCH/DELETE endpoints should exist "
                        + "in audit or artifact controllers "
                        + "(Constitution IX.3, XXI.4)")
                .isEmpty();
    }
}
