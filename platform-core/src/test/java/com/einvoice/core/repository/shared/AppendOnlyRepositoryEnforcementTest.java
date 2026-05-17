package com.einvoice.core.repository.shared;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class AppendOnlyRepositoryEnforcementTest {

    private static final Set<String> ALLOWLISTED_METHODS = Set.of(
            "finalizeAttempt"
    );

    private static final List<Class<?>> REPOSITORIES = List.of(
            SubmissionAttemptRepository.class,
            InvoiceArtifactRepository.class,
            AuditLogRepository.class
    );

    /**
     * JpaSpecificationExecutor adds a single Specification-typed `delete` overload
     * in Spring Data JPA 3+. We accept it because it is required to enable
     * `findAll(Specification, Pageable)` for paged queries, and the V52
     * append_only_guard DB trigger blocks the resulting SQL regardless.
     */
    private static boolean isAllowedSpecificationDelete(Method m) {
        return "delete".equals(m.getName())
                && m.getParameterCount() == 1
                && Specification.class.isAssignableFrom(m.getParameterTypes()[0]);
    }

    @Test
    void noDeleteOrUpdateMethods_exceptAllowlisted() {
        for (Class<?> repo : REPOSITORIES) {
            for (Method method : repo.getMethods()) {
                String name = method.getName();
                boolean isDeleteOrUpdate = name.startsWith("delete") || name.startsWith("update");
                if (isDeleteOrUpdate
                        && !ALLOWLISTED_METHODS.contains(name)
                        && !isAllowedSpecificationDelete(method)) {
                    fail(repo.getSimpleName() + " exposes forbidden method: " + name);
                }
            }
        }
    }

    @Test
    void noModifyingQueryAnnotations_exceptAllowlisted() {
        for (Class<?> repo : REPOSITORIES) {
            for (Method method : repo.getDeclaredMethods()) {
                if (ALLOWLISTED_METHODS.contains(method.getName())) {
                    continue;
                }
                boolean hasModifying = Arrays.stream(method.getAnnotations())
                        .anyMatch(a -> a.annotationType().getSimpleName().equals("Modifying"));
                if (hasModifying) {
                    fail(repo.getSimpleName() + "." + method.getName()
                            + " has @Modifying but is not allowlisted");
                }
            }
        }
    }

    @Test
    void finalizeAttempt_isOnlyAllowlistedModifyingMethod() {
        boolean found = Arrays.stream(SubmissionAttemptRepository.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("finalizeAttempt")
                        && Arrays.stream(m.getAnnotations())
                                .anyMatch(a -> a.annotationType().getSimpleName().equals("Modifying")));
        assertTrue(found, "SubmissionAttemptRepository.finalizeAttempt should have @Modifying");
    }

    @Test
    void noInheritedDeleteOrFlushMethods_fromObjectOrSpring() {
        Set<String> dangerous = Set.of(
                "deleteById", "deleteAll", "deleteAllInBatch",
                "deleteAllById", "deleteAllByIdInBatch",
                "saveAndFlush", "flush");
        for (Class<?> repo : REPOSITORIES) {
            for (Method method : repo.getMethods()) {
                if (dangerous.contains(method.getName())) {
                    fail(repo.getSimpleName() + " inherits forbidden method: " + method.getName());
                }
                if ("delete".equals(method.getName())
                        && !isAllowedSpecificationDelete(method)) {
                    fail(repo.getSimpleName()
                            + " inherits non-Specification delete overload");
                }
            }
        }
    }
}
