package com.einvoice.core.service;

import com.einvoice.core.domain.enums.Authority;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Resolves authority engine implementations by authority type. */
@Service
public class AuthorityEngineFactory {

    private final Map<Authority, AuthorityEngine> engines;

    /**
     * Constructs the factory, auto-registering all engine beans by their supported authority.
     *
     * @param engineBeans Spring-injected map of engine bean names to instances
     */
    public AuthorityEngineFactory(Map<String, AuthorityEngine> engineBeans) {
        this.engines = new EnumMap<>(Authority.class);
        for (Map.Entry<String, AuthorityEngine> entry : engineBeans.entrySet()) {
            AuthorityEngine engine = entry.getValue();
            engines.put(engine.getSupportedAuthority(), engine);
        }
    }

    /**
     * Retrieves the engine registered for the given authority.
     *
     * @param authority the authority type
     * @return the engine implementation
     */
    public AuthorityEngine getEngine(Authority authority) {
        AuthorityEngine engine = engines.get(authority);
        if (engine == null) {
            throw new IllegalArgumentException("No engine registered for authority: " + authority);
        }
        return engine;
    }

    public boolean hasEngine(Authority authority) {
        return engines.containsKey(authority);
    }
}
