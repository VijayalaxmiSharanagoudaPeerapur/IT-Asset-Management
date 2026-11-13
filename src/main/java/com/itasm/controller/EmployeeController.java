package com.itasm.controller;

import com.itasm.dto.PageResponse;
import com.itasm.dto.asset.AssetResponse;
import com.itasm.dto.employee.EmployeeRequest;
import com.itasm.dto.employee.EmployeeResponse;
import com.itasm.service.AssetService;
import com.itasm.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "2. Employee Management")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final AssetService assetService;

    @GetMapping
    @Operation(summary = "Search employees with filter and pagination")
    public PageResponse<EmployeeResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return PageResponse.of(employeeService.search(q, department, active,
                PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @GetMapping("/me")
    public EmployeeResponse getMe() {
        return employeeService.getMe();
    }

    @PutMapping("/me")
    public EmployeeResponse updateMe(@Valid @RequestBody EmployeeRequest request) {
        return employeeService.updateMe(request);
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @GetMapping("/{id}/assets")
    public List<AssetResponse> assets(@PathVariable Long id) {
        employeeService.get(id);
        return assetService.byEmployee(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete / deactivate employee (assets must be unassigned first)")
    public void deactivate(@PathVariable Long id) {
        employeeService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reactivate employee account")
    public void activate(@PathVariable Long id) {
        employeeService.activate(id);
    }
}
