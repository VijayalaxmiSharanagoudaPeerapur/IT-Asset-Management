package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.HistoryAction;
import com.itasm.dto.asset.*;
import com.itasm.entity.Asset;
import com.itasm.entity.Employee;
import com.itasm.entity.UserAccount;
import com.itasm.exception.InvalidOperationException;
import org.springframework.security.access.AccessDeniedException;
import com.itasm.repository.AssetHistoryRepository;
import com.itasm.repository.AssetRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.AssetService;
import com.itasm.service.EmployeeService;
import com.itasm.service.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkOperationsTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetHistoryRepository historyRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private Mapper mapper;
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private AssetService assetService;

    private Employee activeEmp;
    private Asset asset1;
    private Asset asset2;
    private AssetResponse resp1;
    private AssetResponse resp2;

    @BeforeEach
    void setUp() {
        activeEmp = Employee.builder()
                .id(10L)
                .firstName("Alice")
                .lastName("Smith")
                .employeeCode("EMP-010")
                .active(true)
                .build();

        asset1 = Asset.builder()
                .id(1L)
                .assetTag("LAP-101")
                .model("ThinkPad T14")
                .status(AssetStatus.AVAILABLE)
                .build();

        asset2 = Asset.builder()
                .id(2L)
                .assetTag("LAP-102")
                .model("Latitude 5540")
                .status(AssetStatus.AVAILABLE)
                .build();

        resp1 = new AssetResponse(1L, "LAP-101", "SN-1", "Laptop", "Lenovo", "ThinkPad T14",
                null, null, null, AssetStatus.ASSIGNED, 10L, "Alice Smith", null, null, 0);
        resp2 = new AssetResponse(2L, "LAP-102", "SN-2", "Laptop", "Dell", "Latitude 5540",
                null, null, null, AssetStatus.ASSIGNED, 10L, "Alice Smith", null, null, 0);
    }

    @Test
    void testBulkAssignSuccess() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(employeeService.find(10L)).thenReturn(activeEmp);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset1));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(asset2));
        when(mapper.toAsset(asset1)).thenReturn(resp1);
        when(mapper.toAsset(asset2)).thenReturn(resp2);

        BulkAssignRequest request = new BulkAssignRequest(List.of(1L, 2L), 10L, "Q4 deployment");
        BulkOperationResponse response = assetService.bulkAssign(request);

        assertTrue(response.success());
        assertEquals(2, response.successCount());
        assertEquals(0, response.failureCount());
        assertEquals(AssetStatus.ASSIGNED, asset1.getStatus());
        assertEquals(activeEmp, asset1.getAssignedEmployee());
        assertEquals(AssetStatus.ASSIGNED, asset2.getStatus());
        assertEquals(activeEmp, asset2.getAssignedEmployee());
        verify(historyRepository, times(2)).save(any());
    }

    @Test
    void testBulkAssignFailsSafelyWhenAssetIsRetired() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(employeeService.find(10L)).thenReturn(activeEmp);

        asset2.setStatus(AssetStatus.RETIRED);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset1));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(asset2));

        BulkAssignRequest request = new BulkAssignRequest(List.of(1L, 2L), 10L, "Deploy");
        BulkOperationResponse response = assetService.bulkAssign(request);

        assertFalse(response.success());
        assertEquals(0, response.successCount());
        assertEquals(1, response.failureCount());
        assertTrue(response.errors().get(0).contains("is RETIRED and cannot be assigned"));
        // Ensure no partial state corruption occurred!
        assertEquals(AssetStatus.AVAILABLE, asset1.getStatus());
        assertNull(asset1.getAssignedEmployee());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void testBulkAssignFailsWhenEmployeeInactive() {
        when(currentUser.isAdmin()).thenReturn(true);
        activeEmp.setActive(false);
        when(employeeService.find(10L)).thenReturn(activeEmp);

        BulkAssignRequest request = new BulkAssignRequest(List.of(1L), 10L, "Deploy");
        BulkOperationResponse response = assetService.bulkAssign(request);

        assertFalse(response.success());
        assertTrue(response.errors().get(0).contains("is inactive"));
        verify(assetRepository, never()).findById(any());
    }

    @Test
    void testBulkStatusSuccess() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset1));
        when(assetRepository.findById(2L)).thenReturn(Optional.of(asset2));
        when(mapper.toAsset(asset1)).thenReturn(resp1);
        when(mapper.toAsset(asset2)).thenReturn(resp2);

        BulkStatusRequest request = new BulkStatusRequest(List.of(1L, 2L), AssetStatus.REPAIR, "Annual inspection");
        BulkOperationResponse response = assetService.bulkChangeStatus(request);

        assertTrue(response.success());
        assertEquals(2, response.successCount());
        assertEquals(AssetStatus.REPAIR, asset1.getStatus());
        assertEquals(AssetStatus.REPAIR, asset2.getStatus());
        verify(historyRepository, times(2)).save(any());
    }

    @Test
    void testBulkStatusRejectsAssignedWithoutEmployee() {
        when(currentUser.isAdmin()).thenReturn(true);

        BulkStatusRequest request = new BulkStatusRequest(List.of(1L), AssetStatus.ASSIGNED, "Attempt direct assign");
        BulkOperationResponse response = assetService.bulkChangeStatus(request);

        assertFalse(response.success());
        assertTrue(response.errors().get(0).contains("Cannot bulk change status to ASSIGNED directly"));
        verify(assetRepository, never()).findById(any());
    }

    @Test
    void testBulkRetireSuccess() {
        when(currentUser.isAdmin()).thenReturn(true);
        asset1.setStatus(AssetStatus.ASSIGNED);
        asset1.setAssignedEmployee(activeEmp);

        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset1));
        when(mapper.toAsset(asset1)).thenReturn(resp1);

        BulkRetireRequest request = new BulkRetireRequest(List.of(1L), "Hardware obsolescence");
        BulkOperationResponse response = assetService.bulkRetire(request);

        assertTrue(response.success());
        assertEquals(1, response.successCount());
        assertEquals(AssetStatus.RETIRED, asset1.getStatus());
        assertNull(asset1.getAssignedEmployee()); // Employee must be unassigned when retired
        verify(historyRepository, times(1)).save(any());
    }

    @Test
    void testNonAdminCannotPerformBulkOperations() {
        when(currentUser.isAdmin()).thenReturn(false);

        BulkAssignRequest request = new BulkAssignRequest(List.of(1L), 10L, "Unauthorized");
        assertThrows(AccessDeniedException.class, () -> assetService.bulkAssign(request));
    }
}
