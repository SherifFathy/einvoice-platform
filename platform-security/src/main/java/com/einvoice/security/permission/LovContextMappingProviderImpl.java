package com.einvoice.security.permission;

import com.einvoice.core.context.LovContextMappingProvider;
import org.springframework.stereotype.Component;

/** Delegates LOV context environment resolution to {@link LovContextMapper}. */
@Component
public class LovContextMappingProviderImpl implements LovContextMappingProvider {

    @Override
    public String toAuthorityEnvironment(String authority, String subEnv) {
        return LovContextMapper.toAuthorityEnvironment(authority, subEnv);
    }
}
