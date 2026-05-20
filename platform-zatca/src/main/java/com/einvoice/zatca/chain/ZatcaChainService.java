package com.einvoice.zatca.chain;

import com.einvoice.core.domain.zatca.ZatcaChainState;
import com.einvoice.core.error.ChainBusyException;
import com.einvoice.core.repository.zatca.ZatcaChainStateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages ZATCA chain-state locking, retrieval and advancement. */
@Service
public class ZatcaChainService {

    private final ZatcaChainStateRepository chainStateRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public ZatcaChainService(ZatcaChainStateRepository chainStateRepository) {
        this.chainStateRepository = chainStateRepository;
    }

    /**
     * Uses default REQUIRED propagation so the SELECT FOR UPDATE lock
     * is held within the caller's transaction until commit. REQUIRES_NEW
     * would release the lock early, defeating the purpose.
     *
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return the locked chain-state row
     */
    @Transactional
    public ZatcaChainState acquireForUpdate(UUID companyId,
            Short authorityEnvironmentId) {
        try {
            entityManager.createNativeQuery(
                    "SET LOCAL lock_timeout = '30s'").executeUpdate();
            return chainStateRepository
                    .findForUpdate(companyId, authorityEnvironmentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No chain state for company=" + companyId
                                    + " env=" + authorityEnvironmentId));
        } catch (PessimisticLockingFailureException ex) {
            throw new ChainBusyException(
                    "Chain is busy for company=" + companyId
                            + " env=" + authorityEnvironmentId);
        }
    }

    /**
     * Advances the chain counter and updates the previous-invoice hash.
     *
     * @param chainRow the locked chain-state row to advance
     * @param newHash the new invoice hash to store
     * @return the persisted and flushed chain-state row
     */
    @Transactional
    public ZatcaChainState advance(ZatcaChainState chainRow,
            String newHash) {
        chainRow.setInvoiceCounter(chainRow.getInvoiceCounter() + 1);
        chainRow.setPreviousInvoiceHash(newHash);
        return chainStateRepository.saveAndFlush(chainRow);
    }
}
