package com.itasm.controller;

import com.itasm.dto.request.CreateAssetRequestDTO;
import com.itasm.dto.request.EmployeeAssetRequestDTO;
import com.itasm.dto.request.ResolveAssetRequestDTO;
import com.itasm.service.EmployeeAssetRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/asset-requests")
@RequiredArgsConstructor
@Tag(name = "4. Asset Requests")
public class EmployeeAssetRequestController {

    private final EmployeeAssetRequestService requestService;

    @GetMapping
    @Operation(summary = "Admin: Get all asset requests")
    public List<EmployeeAssetRequestDTO> getAllRequests() {
        return requestService.getAllRequests();
    }

    @GetMapping("/me")
    @Operation(summary = "Employee: Get my asset requests")
    public List<EmployeeAssetRequestDTO> getMyRequests() {
        return requestService.getMyRequests();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Employee: Create a new asset request")
    public EmployeeAssetRequestDTO createRequest(@Valid @RequestBody CreateAssetRequestDTO request) {
        return requestService.createRequest(request);
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Admin: Approve or reject an asset request")
    public EmployeeAssetRequestDTO resolveRequest(@PathVariable Long id, @Valid @RequestBody ResolveAssetRequestDTO request) {
        return requestService.resolveRequest(id, request);
    }
}
