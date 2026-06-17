package com.einvoice.api.branch;

import com.einvoice.api.branch.dto.BranchLookupResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only branch lookup endpoints for operational filters. */
@RestController
@RequestMapping("/api/branches")
public class BranchLookupController {

    private final BranchLookupService service;

    public BranchLookupController(BranchLookupService service) {
        this.service = service;
    }

    @GetMapping
    public List<BranchLookupResponse> listVisibleBranches() {
        return service.listVisibleBranches();
    }
}
