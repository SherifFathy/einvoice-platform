package com.einvoice.core.context;

/** Resolves the effective LOV context ID, accounting for super-user bypass. */
public interface LovContextResolver {

    Long resolveEffectiveLovContextId();
}
