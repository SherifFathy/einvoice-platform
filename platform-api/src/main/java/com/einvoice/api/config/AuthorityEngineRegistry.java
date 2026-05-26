package com.einvoice.api.config;

import com.einvoice.core.authority.AuthorityEngine;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.eta.engine.EtaAuthorityEngine;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registry of authority engines keyed by transaction type. */
@Configuration
public class AuthorityEngineRegistry {

    @Bean
    public Map<TransactionType, AuthorityEngine> authorityEngineMap(
            EtaAuthorityEngine etaEngine,
            ZatcaAuthorityEngine zatcaEngine) {
        Map<TransactionType, AuthorityEngine> map =
                new EnumMap<>(TransactionType.class);
        map.put(TransactionType.INVOICE, etaEngine);
        map.put(TransactionType.RECEIPT, etaEngine);
        map.put(TransactionType.STANDARD, zatcaEngine);
        map.put(TransactionType.SIMPLIFIED, zatcaEngine);
        return map;
    }
}
