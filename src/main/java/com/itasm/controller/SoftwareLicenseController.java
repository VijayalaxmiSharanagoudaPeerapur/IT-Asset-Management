package com.itasm.controller;

import com.itasm.dto.software.*;
import com.itasm.service.SoftwareLicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/software")
@RequiredArgsConstructor
@Tag(name = "5. Software License Management")
public class SoftwareLicenseController {

    private final SoftwareLicenseService softwareService;

    @GetMapping
    @Operation(summary = "Get all software licenses")
    public List<SoftwareLicenseDTO> getAllLicenses() {
        return softwareService.getAllLicenses();
    }

    @GetMapping("/me")
    @Operation(summary = "Employee: Get my assigned software")
    public List<SoftwareAssignmentDTO> getMySoftware() {
        return softwareService.getMySoftware();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get software license details")
    public SoftwareLicenseDTO getLicense(@PathVariable Long id) {
        return softwareService.getLicense(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Admin: Create a new software license")
    public SoftwareLicenseDTO createLicense(@Valid @RequestBody SoftwareLicenseRequestDTO request) {
        return softwareService.createLicense(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Admin: Update a software license")
    public SoftwareLicenseDTO updateLicense(@PathVariable Long id, @Valid @RequestBody SoftwareLicenseRequestDTO request) {
        return softwareService.updateLicense(id, request);
    }

    @GetMapping("/{id}/assignments")
    @Operation(summary = "Get all assignments for a specific license")
    public List<SoftwareAssignmentDTO> getAssignmentsByLicense(@PathVariable Long id) {
        return softwareService.getAssignmentsByLicense(id);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Admin: Assign a license seat to an employee")
    public SoftwareAssignmentDTO assignLicense(@PathVariable Long id, @Valid @RequestBody AssignSoftwareRequestDTO request) {
        return softwareService.assignLicense(id, request);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Admin: Unassign a license from an employee")
    public void unassignLicense(@PathVariable Long assignmentId) {
        softwareService.unassignLicense(assignmentId);
    }
}
