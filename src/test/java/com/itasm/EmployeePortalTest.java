package com.itasm;

import com.itasm.domain.RequestStatus;
import com.itasm.dto.request.CreateAssetRequestDTO;
import com.itasm.dto.request.EmployeeAssetRequestDTO;
import com.itasm.dto.request.ResolveAssetRequestDTO;
import com.itasm.entity.Employee;
import com.itasm.entity.EmployeeAssetRequest;
import com.itasm.exception.InvalidOperationException;
import org.springframework.security.access.AccessDeniedException;
import com.itasm.repository.EmployeeAssetRequestRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.EmployeeAssetRequestService;
import com.itasm.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeePortalTest {

    @Mock
    private EmployeeAssetRequestRepository requestRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private EmployeeAssetRequestService requestService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id(10L)
                .firstName("Sarah")
                .lastName("Connor")
                .employeeCode("EMP-010")
                .department("Security Operations")
                .active(true)
                .build();
    }

    @Test
    void createRequest_CreatesPendingAssetRequest() {
        when(currentUser.requireEmployee()).thenReturn(employee);

        CreateAssetRequestDTO dto = new CreateAssetRequestDTO("Ergonomic Monitor Stand", "Prevent neck strain");

        EmployeeAssetRequest savedReq = EmployeeAssetRequest.builder()
                .id(1L)
                .employee(employee)
                .requestedItem("Ergonomic Monitor Stand")
                .reason("Prevent neck strain")
                .status(RequestStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.save(any(EmployeeAssetRequest.class))).thenReturn(savedReq);

        EmployeeAssetRequestDTO res = requestService.createRequest(dto);

        assertNotNull(res);
        assertEquals(1L, res.id());
        assertEquals(10L, res.employeeId());
        assertEquals("Sarah Connor", res.employeeName());
        assertEquals("Ergonomic Monitor Stand", res.requestedItem());
        assertEquals(RequestStatus.PENDING, res.status());
        verify(requestRepository).save(any(EmployeeAssetRequest.class));
    }

    @Test
    void getMyRequests_ReturnsOnlyCurrentEmployeeRequests() {
        when(currentUser.requireEmployee()).thenReturn(employee);

        EmployeeAssetRequest req = EmployeeAssetRequest.builder()
                .id(2L)
                .employee(employee)
                .requestedItem("Noise Cancelling Headphones")
                .reason("Open office noise")
                .status(RequestStatus.APPROVED)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.findByEmployee_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(req));

        List<EmployeeAssetRequestDTO> list = requestService.getMyRequests();

        assertEquals(1, list.size());
        assertEquals("Noise Cancelling Headphones", list.get(0).requestedItem());
        assertEquals(RequestStatus.APPROVED, list.get(0).status());
    }

    @Test
    void resolveRequest_AdminApprovesRequest() {
        when(currentUser.isAdmin()).thenReturn(true);

        EmployeeAssetRequest pending = EmployeeAssetRequest.builder()
                .id(3L)
                .employee(employee)
                .requestedItem("USB-C Hub")
                .reason("Multiple peripherals")
                .status(RequestStatus.PENDING)
                .build();

        when(requestRepository.findById(3L)).thenReturn(Optional.of(pending));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResolveAssetRequestDTO resolveDTO = new ResolveAssetRequestDTO(RequestStatus.APPROVED, "Approved by IT Management");

        EmployeeAssetRequestDTO result = requestService.resolveRequest(3L, resolveDTO);

        assertEquals(RequestStatus.APPROVED, result.status());
        assertEquals("Approved by IT Management", result.adminNotes());
        assertNotNull(result.resolvedAt());
    }

    @Test
    void resolveRequest_AlreadyResolved_ThrowsException() {
        when(currentUser.isAdmin()).thenReturn(true);

        EmployeeAssetRequest alreadyResolved = EmployeeAssetRequest.builder()
                .id(4L)
                .employee(employee)
                .requestedItem("Keypad")
                .status(RequestStatus.REJECTED)
                .build();

        when(requestRepository.findById(4L)).thenReturn(Optional.of(alreadyResolved));

        ResolveAssetRequestDTO resolveDTO = new ResolveAssetRequestDTO(RequestStatus.APPROVED, "Re-trying approval");

        assertThrows(InvalidOperationException.class, () ->
                requestService.resolveRequest(4L, resolveDTO)
        );
    }

    @Test
    void resolveRequest_NonAdmin_ThrowsException() {
        when(currentUser.isAdmin()).thenReturn(false);

        ResolveAssetRequestDTO resolveDTO = new ResolveAssetRequestDTO(RequestStatus.APPROVED, "Notes");

        assertThrows(AccessDeniedException.class, () ->
                requestService.resolveRequest(5L, resolveDTO)
        );
    }
}
