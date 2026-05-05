package com.einvoice.api.admin;

import com.einvoice.api.admin.dto.BranchCreateRequest;
import com.einvoice.api.admin.dto.BranchResponse;
import com.einvoice.api.admin.dto.BranchUpdateRequest;
import com.einvoice.api.admin.service.AdminBranchService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing branches under companies. */
@RestController
@RequestMapping("/api/admin")
public class AdminBranchController {

    private final AdminBranchService service;

    public AdminBranchController(AdminBranchService service) {
        this.service = service;
    }

    @GetMapping("/companies/{id}/branches")
    public List<BranchResponse> listForCompany(@PathVariable UUID id) {
        return service.listForCompany(id);
    }

    @PostMapping("/companies/{id}/branches")
    public ResponseEntity<BranchResponse> create(@PathVariable UUID id,
            @Valid @RequestBody BranchCreateRequest request) {
        BranchResponse response = service.create(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/branches/{id}")
    public BranchResponse update(@PathVariable UUID id, @Valid @RequestBody BranchUpdateRequest request) {
        return service.update(id, request);
    }
}
