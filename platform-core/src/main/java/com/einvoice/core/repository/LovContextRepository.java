package com.einvoice.core.repository;

import com.einvoice.core.domain.LovContext;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for accessing LOV context definitions. */
@Repository
public interface LovContextRepository extends JpaRepository<LovContext, Long> {

    Optional<LovContext> findByContextKey(String contextKey);
}
