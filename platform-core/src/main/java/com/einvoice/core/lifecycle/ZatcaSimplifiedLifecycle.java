package com.einvoice.core.lifecycle;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.domain.shared.LifecycleTransitions;
import com.einvoice.core.domain.shared.TransactionType;

/** Convenience facade over {@link LifecycleTransitions} for ZATCA simplified documents. */
public final class ZatcaSimplifiedLifecycle {

    private ZatcaSimplifiedLifecycle() {
    }

    public static boolean allowed(DocumentState from, LifecycleAction action) {
        return LifecycleTransitions.allowed(from, action,
                TransactionType.SIMPLIFIED);
    }

    public static DocumentState next(DocumentState from,
            LifecycleAction action) {
        return LifecycleTransitions.next(from, action,
                TransactionType.SIMPLIFIED);
    }
}
