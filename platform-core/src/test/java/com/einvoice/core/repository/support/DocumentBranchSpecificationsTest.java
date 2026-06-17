package com.einvoice.core.repository.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

/** Unit coverage for branch filter specification factories. */
class DocumentBranchSpecificationsTest {

    private final UUID branchId = UUID.randomUUID();
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final Path<Object> branchPath = mockPath();
    private final Predicate predicate = mock(Predicate.class);

    @Test
    void etaInvoiceForBranchFiltersOnBranchId() {
        Root<EtaInvoiceHeader> root = mockRoot();
        assertBranchPredicate(EtaInvoiceSpecifications.forBranch(branchId),
                root);
    }

    @Test
    void etaReceiptForBranchFiltersOnBranchId() {
        Root<EtaReceiptHeader> root = mockRoot();
        assertBranchPredicate(EtaReceiptSpecifications.forBranch(branchId),
                root);
    }

    @Test
    void zatcaStandardForBranchFiltersOnBranchId() {
        Root<ZatcaStandardHeader> root = mockRoot();
        assertBranchPredicate(ZatcaStandardSpecifications.forBranch(branchId),
                root);
    }

    @Test
    void zatcaSimplifiedForBranchFiltersOnBranchId() {
        Root<ZatcaSimplifiedHeader> root = mockRoot();
        assertBranchPredicate(
                ZatcaSimplifiedSpecifications.forBranch(branchId), root);
    }

    private <T> void assertBranchPredicate(Specification<T> spec,
            Root<T> root) {
        when(root.get("branchId")).thenReturn(branchPath);
        when(cb.equal(branchPath, branchId)).thenReturn(predicate);

        assertThat(spec.toPredicate(root, query, cb)).isSameAs(predicate);
        verify(root).get("branchId");
        verify(cb).equal(branchPath, branchId);
    }

    @SuppressWarnings("unchecked")
    private static <T> Root<T> mockRoot() {
        return mock(Root.class);
    }

    @SuppressWarnings("unchecked")
    private static Path<Object> mockPath() {
        return mock(Path.class);
    }
}
