package com.einvoice.core.repository.support;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification factories for operational-data compound-tenancy filtering.
 *
 * <p>The parameterless {@link #inActiveTenant()} reads tenant values from a
 * static {@link Supplier} that must be wired at application boot by
 * {@code OperationalRepositorySupportConfig} (in platform-security).
 * Test slices that do not load that config must either call
 * {@link #setTenantValuesSupplier(Supplier)} explicitly or use the
 * explicit-parameter overload
 * {@link #inActiveTenant(UUID, Short)}.</p>
 */
public final class OperationalRepositorySupport {

    private static volatile Supplier<TenantValues> tenantValuesSupplier;

    private OperationalRepositorySupport() {
    }

    public record TenantValues(UUID companyId, Short authorityEnvironmentId) {
    }

    public static void setTenantValuesSupplier(Supplier<TenantValues> supplier) {
        tenantValuesSupplier = supplier;
    }

    public static <T> Specification<T> companyIdEquals(UUID companyId) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("companyId"), companyId);
    }

    public static <T> Specification<T> authorityEnvironmentIdEquals(Short authorityEnvironmentId) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("authorityEnvironmentId"), authorityEnvironmentId);
    }

    /**
     * Compound filter that constrains queries to the current JWT-derived tenant.
     * Must only be used when {@code OperationalRepositorySupportConfig} has
     * wired the supplier at boot time.
     *
     * @param <T> entity type
     * @return specification combining companyId and authorityEnvironmentId predicates
     */
    public static <T> Specification<T> inActiveTenant() {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (tenantValuesSupplier == null) {
                throw new IllegalStateException(
                        "TenantValuesSupplier not configured");
            }
            TenantValues vals = tenantValuesSupplier.get();
            if (vals == null || vals.companyId() == null || vals.authorityEnvironmentId() == null) {
                throw new IllegalStateException(
                        "Tenant context must be populated before querying operational data");
            }
            Predicate companyPred = cb.equal(root.get("companyId"), vals.companyId());
            Predicate envPred = cb.equal(root.get("authorityEnvironmentId"),
                    vals.authorityEnvironmentId());
            return cb.and(companyPred, envPred);
        };
    }

    /**
     * Convenience overload that accepts explicit tenant values instead of
     * reading from the static supplier. Useful for repository default methods
     * that already have companyId and authorityEnvironmentId in scope.
     *
     * @param <T> entity type
     * @param companyId company identifier
     * @param authorityEnvironmentId environment short code
     * @return specification combining companyId and authorityEnvironmentId predicates
     */
    public static <T> Specification<T> inActiveTenant(UUID companyId, Short authorityEnvironmentId) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate companyPred = cb.equal(root.get("companyId"), companyId);
            Predicate envPred = cb.equal(root.get("authorityEnvironmentId"), authorityEnvironmentId);
            return cb.and(companyPred, envPred);
        };
    }

    /**
     * Single-column equality on {@code id}. Intended for composing with
     * {@link #inActiveTenant()} so that lookups are always tenant-scoped.
     *
     * @param <T> entity type
     * @param id entity primary key
     * @return specification matching the given id
     */
    public static <T> Specification<T> idEquals(UUID id) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("id"), id);
    }

    /**
     * Filter that matches entities belonging to any of the given company IDs.
     * Returns a disjunction (always false) when the collection is empty.
     *
     * @param <T> entity type
     * @param companyIds collection of company identifiers to match
     * @return specification matching any of the given company IDs
     */
    public static <T> Specification<T> companyIdIn(Collection<UUID> companyIds) {
        if (companyIds.isEmpty()) {
            return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                    cb.disjunction();
        }
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                root.get("companyId").in(companyIds);
    }
}
