package com.einvoice.core.repository;

import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Base repository that exposes only save and read operations.
 * Extends the bare {@link Repository} marker interface so that no delete or bulk-mutate
 * methods are inherited — enforcing append-only semantics at the Java level.
 *
 * @param <T> the domain type
 * @param <IdT> the id type
 */
@NoRepositoryBean
public interface AppendOnlyRepository<T, IdT> extends Repository<T, IdT> {

    <S extends T> S save(S entity);

    Optional<T> findById(IdT id);

    Iterable<T> findAll();

    boolean existsById(IdT id);

    long count();
}
