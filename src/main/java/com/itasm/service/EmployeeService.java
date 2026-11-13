package com.itasm.service;

import com.itasm.dto.employee.EmployeeRequest;
import com.itasm.dto.employee.EmployeeResponse;
import com.itasm.entity.Employee;
import com.itasm.entity.UserAccount;
import com.itasm.domain.Role;
import com.itasm.exception.DuplicateResourceException;
import com.itasm.exception.InvalidOperationException;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.EmployeeRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final AssetRepository assetRepository;
    private final PasswordEncoder passwordEncoder;
    private final Mapper mapper;
    private final AuditLogService auditLogService;
    private final CurrentUser currentUser;

    public Page<EmployeeResponse> search(String q, String department, Boolean active, Pageable pageable) {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can list employees");
        }
        return employeeRepository.search(q, department, active, pageable).map(mapper::toEmployee);
    }

    public EmployeeResponse get(Long id) {
        Employee employee = find(id);
        if (!currentUser.isAdmin()) {
            Employee me = currentUser.requireEmployee();
            if (!me.getId().equals(id)) {
                throw new AccessDeniedException("Employees can only view their own profile");
            }
        }
        return mapper.toEmployee(employee);
    }

    public EmployeeResponse getMe() {
        return mapper.toEmployee(currentUser.requireEmployee());
    }

    @Transactional
    public EmployeeResponse updateMe(EmployeeRequest request) {
        Employee employee = currentUser.requireEmployee();
        employee.setPhone(request.phone());
        if (request.password() != null && !request.password().isBlank() && employee.getUser() != null) {
            validatePasswordComplexity(request.password());
            employee.getUser().setPassword(passwordEncoder.encode(request.password()));
        }
        auditLogService.logAction("Employee", employee.getId(), "UPDATE_ME", null, "Updated own profile");
        return mapper.toEmployee(employee);
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        requireAdmin();
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())
                || userAccountRepository.existsByUsername(request.username())
                || employeeRepository.existsByEmail(request.email())
                || userAccountRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Employee code, username, or email already exists");
        }
        String password = request.password() == null ? "Pass@123" : request.password();
        validatePasswordComplexity(password);
        UserAccount user = UserAccount.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(password))
                .role(Role.EMPLOYEE)
                .enabled(true)
                .build();
        userAccountRepository.save(user);

        Employee employee = Employee.builder()
                .employeeCode(request.employeeCode())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .department(request.department())
                .designation(request.designation())
                .phone(request.phone())
                .active(true)
                .user(user)
                .build();
        employee = employeeRepository.save(employee);
        auditLogService.logAction("Employee", employee.getId(), "CREATE", null, "Created employee " + employee.getFullName());
        return mapper.toEmployee(employee);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        requireAdmin();
        Employee employee = find(id);
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setDepartment(request.department());
        employee.setDesignation(request.designation());
        employee.setPhone(request.phone());
        if (request.password() != null && !request.password().isBlank() && employee.getUser() != null) {
            validatePasswordComplexity(request.password());
            employee.getUser().setPassword(passwordEncoder.encode(request.password()));
        }
        auditLogService.logAction("Employee", employee.getId(), "UPDATE", null, "Updated employee " + employee.getFullName());
        return mapper.toEmployee(employee);
    }

    @Transactional
    public void deactivate(Long id) {
        requireAdmin();
        Employee employee = find(id);
        if (!assetRepository.findByAssignedEmployee_Id(id).isEmpty()) {
            throw new InvalidOperationException(
                    "Unassign all assets before deactivating this employee");
        }
        employee.setActive(false);
        if (employee.getUser() != null) {
            employee.getUser().setEnabled(false);
        }
        auditLogService.logAction("Employee", employee.getId(), "DEACTIVATE", "active=true", "active=false");
    }

    @Transactional
    public void activate(Long id) {
        requireAdmin();
        Employee employee = find(id);
        employee.setActive(true);
        if (employee.getUser() != null) {
            employee.getUser().setEnabled(true);
        }
    }

    public Employee find(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can manage employees");
        }
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.isBlank()) {
            return; // allowed to be empty if not updating
        }
        if (password.length() < 8) {
            throw new InvalidOperationException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidOperationException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new InvalidOperationException("Password must contain at least one number");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new InvalidOperationException("Password must contain at least one special character");
        }
    }
}
