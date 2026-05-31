package com.einvoice.core.repository.company;

import com.einvoice.core.domain.company.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Javadoc. */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findByIsActiveTrue();

    List<Company> findByTaxNumber(String taxNumber);

    Optional<Company> findByTaxNumberAndIsActiveTrue(String taxNumber);
}
