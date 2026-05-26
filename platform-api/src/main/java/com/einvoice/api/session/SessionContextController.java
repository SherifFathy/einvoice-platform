package com.einvoice.api.session;

import com.einvoice.api.session.dto.SessionContextResponse;
import com.einvoice.security.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for retrieving the current session context. */
@RestController
@RequestMapping("/api/session")
public class SessionContextController {

    private final SessionContextAssembler assembler;
    private final SessionContextCache cache;

    public SessionContextController(SessionContextAssembler assembler,
            SessionContextCache cache) {
        this.assembler = assembler;
        this.cache = cache;
    }

    @GetMapping("/context")
    public SessionContextResponse getContext() {
        TenantContext.Holder holder = TenantContext.current();
        return cache.getOrCompute(holder, () -> assembler.assemble(holder));
    }
}
