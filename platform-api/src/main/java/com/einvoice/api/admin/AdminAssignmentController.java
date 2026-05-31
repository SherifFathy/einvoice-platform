package com.einvoice.api.admin;

import com.einvoice.api.admin.dto.AssignmentCreateRequest;
import com.einvoice.api.admin.dto.AssignmentResponse;
import com.einvoice.api.admin.service.AdminAssignmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing user-company transaction-role assignments. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminAssignmentController {

    private final AdminAssignmentService service;

    public AdminAssignmentController(AdminAssignmentService service) {
        this.service = service;
    }

    @GetMapping("/{id}/assignments")
    public List<AssignmentResponse> listForUser(@PathVariable UUID id) {
        return service.listForUser(id);
    }

    @PostMapping("/{id}/assignments")
    public ResponseEntity<AssignmentResponse> create(@PathVariable UUID id,
            @Valid @RequestBody AssignmentCreateRequest request) {
        AssignmentResponse response = service.create(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/assignments/{assignmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable UUID assignmentId) {
        service.delete(id, assignmentId);
        return ResponseEntity.noContent().build();
    }
}
