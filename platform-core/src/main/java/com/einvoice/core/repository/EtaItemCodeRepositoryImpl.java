package com.einvoice.core.repository;

import com.einvoice.core.domain.EtaItemCode;
import com.einvoice.core.domain.enums.EtaItemCodeStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Implementation of custom ETA item code repository queries.
 */
@Repository
public class EtaItemCodeRepositoryImpl implements EtaItemCodeRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<EtaItemCode> findByCompanyIdFiltered(Long companyId,
            EtaItemCodeStatus status, String search, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<EtaItemCode> countRoot = countQuery.from(EtaItemCode.class);
        countQuery.select(cb.count(countRoot))
                .where(buildPredicates(cb, countRoot, companyId, status, search)
                        .toArray(new Predicate[0]));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<EtaItemCode> dataQuery = cb.createQuery(EtaItemCode.class);
        Root<EtaItemCode> dataRoot = dataQuery.from(EtaItemCode.class);
        dataQuery.where(buildPredicates(cb, dataRoot, companyId, status, search)
                        .toArray(new Predicate[0]))
                .orderBy(cb.desc(dataRoot.get("createdAt")));

        List<EtaItemCode> content = entityManager.createQuery(dataQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb,
            Root<EtaItemCode> root, Long companyId,
            EtaItemCodeStatus status, String search) {
        List<Predicate> list = new ArrayList<>();
        list.add(cb.equal(root.get("company").get("id"), companyId));
        if (status != null) {
            list.add(cb.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            list.add(cb.or(
                    cb.like(cb.lower(root.get("itemCode")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
        }
        return list;
    }
}
