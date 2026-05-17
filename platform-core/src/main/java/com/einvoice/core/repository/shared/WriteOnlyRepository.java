package com.einvoice.core.repository.shared;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Append-only marker interface.
 * Explicitly does NOT extend JpaRepository (which would expose delete and update methods).
 * Constitution IX.3, XXI.4 — three-layer defence (repo marker, service, DB trigger).
 */
@NoRepositoryBean
public interface WriteOnlyRepository<T, I> extends Repository<T, I>,
        JpaSpecificationExecutor<T> {

    <S extends T> S save(S entity);

    Optional<T> findById(I id);

    Page<T> findAll(Specification<T> spec, Pageable pageable);
}
