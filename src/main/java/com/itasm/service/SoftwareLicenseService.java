package com.itasm.service;

import com.itasm.domain.SoftwareLicenseStatus;
import com.itasm.dto.software.*;
import com.itasm.entity.Employee;
import com.itasm.entity.SoftwareAssignment;
import com.itasm.entity.SoftwareLicense;
import com.itasm.exception.InvalidOperationException;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.SoftwareAssignmentRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SoftwareLicenseService {

    private final SoftwareLicenseRepository licenseRepository;
    private final SoftwareAssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    public List<SoftwareLicenseDTO> getAllLicenses() {
        return licenseRepository.findAllByOrderByNameAsc().stream()
                .map(this::mapToDTO).toList();
    }

    public SoftwareLicenseDTO getLicense(Long id) {
        return mapToDTO(findLicense(id));
    }

    @Transactional
    public SoftwareLicenseDTO createLicense(SoftwareLicenseRequestDTO request) {
        requireAdmin();
        if (request.totalSeats() < 0) {
            throw new InvalidOperationException("Total seats cannot be negative");
        }
        SoftwareLicense license = SoftwareLicense.builder()
                .name(request.name())
                .vendor(request.vendor())
                .licenseKey(request.licenseKey())
                .totalSeats(request.totalSeats())
                .availableSeats(request.totalSeats())
                .expirationDate(request.expirationDate())
                .build();
        return mapToDTO(licenseRepository.save(license));
    }

    @Transactional
    public SoftwareLicenseDTO updateLicense(Long id, SoftwareLicenseRequestDTO request) {
        requireAdmin();
        if (request.totalSeats() < 0) {
            throw new InvalidOperationException("Total seats cannot be negative");
        }
        SoftwareLicense license = findLicense(id);
        
        int assignedCount = license.getTotalSeats() - license.getAvailableSeats();
        if (request.totalSeats() < assignedCount) {
            throw new InvalidOperationException("Cannot reduce total seats below currently assigned seats (" + assignedCount + ")");
        }
        
        license.setName(request.name());
        license.setVendor(request.vendor());
        license.setLicenseKey(request.licenseKey());
        
        int addedSeats = request.totalSeats() - license.getTotalSeats();
        license.setTotalSeats(request.totalSeats());
        license.setAvailableSeats(Math.max(0, license.getAvailableSeats() + addedSeats));
        
        license.setExpirationDate(request.expirationDate());
        
        return mapToDTO(licenseRepository.save(license));
    }

    @Transactional
    public SoftwareAssignmentDTO assignLicense(Long licenseId, AssignSoftwareRequestDTO request) {
        requireAdmin();
        SoftwareLicense license = findLicense(licenseId);
        if (license.getAvailableSeats() <= 0) {
            throw new InvalidOperationException("No available seats for this software license");
        }
        
        Employee employee = employeeService.find(request.employeeId());
        if (!employee.isActive()) {
            throw new InvalidOperationException("Cannot assign software to an inactive employee");
        }
        
        if (assignmentRepository.existsByLicense_IdAndEmployee_Id(licenseId, employee.getId())) {
            throw new InvalidOperationException("Employee already has this software license assigned");
        }
        
        license.setAvailableSeats(license.getAvailableSeats() - 1);
        licenseRepository.save(license);
        
        SoftwareAssignment assignment = SoftwareAssignment.builder()
                .license(license)
                .employee(employee)
                .build();
                
        return mapToDTO(assignmentRepository.save(assignment));
    }

    @Transactional
    public void unassignLicense(Long assignmentId) {
        requireAdmin();
        SoftwareAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Software assignment not found"));
                
        SoftwareLicense license = assignment.getLicense();
        license.setAvailableSeats(Math.min(license.getTotalSeats(), license.getAvailableSeats() + 1));
        licenseRepository.save(license);
        
        assignmentRepository.delete(assignment);
    }
    
    public List<SoftwareAssignmentDTO> getAssignmentsByLicense(Long licenseId) {
        requireAdmin();
        return assignmentRepository.findByLicense_Id(licenseId).stream()
                .map(this::mapToDTO).toList();
    }
    
    public List<SoftwareAssignmentDTO> getAssignmentsByEmployee(Long employeeId) {
        if (!currentUser.isAdmin()) {
            Employee me = currentUser.requireEmployee();
            if (!me.getId().equals(employeeId)) {
                throw new AccessDeniedException("Employees can only view their own software assignments");
            }
        }
        return assignmentRepository.findByEmployee_Id(employeeId).stream()
                .map(this::mapToDTO).toList();
    }

    public List<SoftwareAssignmentDTO> getMySoftware() {
        return getAssignmentsByEmployee(currentUser.requireEmployee().getId());
    }

    private SoftwareLicense findLicense(Long id) {
        return licenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Software License not found"));
    }

    private SoftwareLicenseDTO mapToDTO(SoftwareLicense license) {
        LocalDate today = LocalDate.now();
        SoftwareLicenseStatus status = SoftwareLicenseStatus.fromExpirationDate(license.getExpirationDate(), today);
        Long daysUntilExpiry = license.getExpirationDate() != null
                ? ChronoUnit.DAYS.between(today, license.getExpirationDate())
                : null;

        String key = license.getLicenseKey();
        if (!currentUser.isAdmin() && key != null && !key.isBlank()) {
            key = maskKey(key);
        }

        return new SoftwareLicenseDTO(
                license.getId(),
                license.getName(),
                license.getVendor(),
                key,
                license.getTotalSeats(),
                license.getAvailableSeats(),
                license.getExpirationDate(),
                status.name(),
                daysUntilExpiry
        );
    }

    private String maskKey(String key) {
        if (key.length() <= 4) {
            return "••••••••";
        }
        return "••••-••••-" + key.substring(key.length() - 4);
    }
    
    private SoftwareAssignmentDTO mapToDTO(SoftwareAssignment assignment) {
        return new SoftwareAssignmentDTO(
                assignment.getId(),
                assignment.getLicense().getId(),
                assignment.getLicense().getName(),
                assignment.getEmployee().getId(),
                assignment.getEmployee().getFullName(),
                assignment.getAssignedAt()
        );
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can manage software licenses");
        }
    }
}
