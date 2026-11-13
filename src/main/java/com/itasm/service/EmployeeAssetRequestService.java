package com.itasm.service;

import com.itasm.domain.RequestStatus;
import com.itasm.dto.request.CreateAssetRequestDTO;
import com.itasm.dto.request.EmployeeAssetRequestDTO;
import com.itasm.dto.request.ResolveAssetRequestDTO;
import com.itasm.entity.Employee;
import com.itasm.entity.EmployeeAssetRequest;
import com.itasm.exception.InvalidOperationException;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.EmployeeAssetRequestRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeAssetRequestService {

    private final EmployeeAssetRequestRepository requestRepository;
    private final EmployeeService employeeService;
    private final CurrentUser currentUser;

    public List<EmployeeAssetRequestDTO> getAllRequests() {
        requireAdmin();
        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToDTO).toList();
    }

    public List<EmployeeAssetRequestDTO> getMyRequests() {
        Employee currentEmployee = currentUser.requireEmployee();
        return requestRepository.findByEmployee_IdOrderByCreatedAtDesc(currentEmployee.getId()).stream()
                .map(this::mapToDTO).toList();
    }

    @Transactional
    public EmployeeAssetRequestDTO createRequest(CreateAssetRequestDTO dto) {
        Employee currentEmployee = currentUser.requireEmployee();
        EmployeeAssetRequest request = EmployeeAssetRequest.builder()
                .employee(currentEmployee)
                .requestedItem(dto.requestedItem())
                .reason(dto.reason())
                .status(RequestStatus.PENDING)
                .build();
        return mapToDTO(requestRepository.save(request));
    }

    @Transactional
    public EmployeeAssetRequestDTO resolveRequest(Long id, ResolveAssetRequestDTO dto) {
        requireAdmin();
        EmployeeAssetRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));
        
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidOperationException("Request is already resolved");
        }
        
        request.setStatus(dto.status());
        request.setAdminNotes(dto.adminNotes());
        request.setResolvedAt(Instant.now());
        
        return mapToDTO(requestRepository.save(request));
    }

    private EmployeeAssetRequestDTO mapToDTO(EmployeeAssetRequest req) {
        return new EmployeeAssetRequestDTO(
            req.getId(),
            req.getEmployee().getId(),
            req.getEmployee().getFullName(),
            req.getRequestedItem(),
            req.getReason(),
            req.getStatus(),
            req.getAdminNotes(),
            req.getCreatedAt(),
            req.getResolvedAt()
        );
    }
    
    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can manage requests");
        }
    }
}
