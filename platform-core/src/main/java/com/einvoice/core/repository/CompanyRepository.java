package com.einvoice.core.repository;

import com.einvoice.core.domain.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for Company entity operations. */
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByVatNumber(String vatNumber);

    boolean existsByVatNumber(String vatNumber);
}
