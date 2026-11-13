package com.itasm;

import com.itasm.domain.SoftwareLicenseStatus;
import com.itasm.dto.software.*;
import com.itasm.entity.Employee;
import com.itasm.entity.SoftwareAssignment;
import com.itasm.entity.SoftwareLicense;
import com.itasm.exception.InvalidOperationException;
import org.springframework.security.access.AccessDeniedException;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.SoftwareAssignmentRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.EmployeeService;
import com.itasm.service.ReportService;
import com.itasm.service.SoftwareLicenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoftwareLicenseModuleTest {

    @Mock
    private SoftwareLicenseRepository licenseRepository;
    @Mock
    private SoftwareAssignmentRepository assignmentRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private TicketRepository ticketRepository;

    private SoftwareLicenseService softwareService;
    private ReportService reportService;

    private Employee activeEmp;

    @BeforeEach
    void setUp() {
        softwareService = new SoftwareLicenseService(licenseRepository, assignmentRepository, employeeService, currentUser);
        reportService = new ReportService(assetRepository, ticketRepository, licenseRepository, currentUser);

        activeEmp = Employee.builder()
                .id(1L)
                .firstName("Vijay")
                .lastName("Kumar")
                .employeeCode("EMP-101")
                .active(true)
                .build();
    }

    @Test
    void emptyDataset_GetAllLicenses_ReturnsEmptyList() {
        when(licenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        List<SoftwareLicenseDTO> result = softwareService.getAllLicenses();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyDataset_ExportCsv_ProducesHeadersOnlyWithoutError() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(licenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        byte[] csvBytes = reportService.exportSoftwareUtilizationCsv();

        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Software Name"));
        assertTrue(csv.contains("Vendor"));
        assertTrue(csv.contains("Total Seats"));
        assertTrue(csv.contains("Allocated Seats"));
        assertTrue(csv.contains("Available Seats"));
        assertTrue(csv.contains("Utilization Rate"));
        assertTrue(csv.contains("Expiration Date"));
        assertTrue(csv.contains("License Status"));
        assertTrue(csv.contains("Days Until Expiry"));
    }

    @Test
    void createLicense_WithVendorAndSeats_PersistsSuccessfully() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(licenseRepository.save(any(SoftwareLicense.class))).thenAnswer(i -> {
            SoftwareLicense l = i.getArgument(0);
            l.setId(10L);
            return l;
        });

        SoftwareLicenseRequestDTO request = new SoftwareLicenseRequestDTO(
                "IntelliJ IDEA Ultimate",
                "JetBrains",
                "JB-ULT-12345-ABCD",
                20,
                LocalDate.now().plusMonths(6)
        );

        SoftwareLicenseDTO result = softwareService.createLicense(request);

        assertNotNull(result);
        assertEquals("IntelliJ IDEA Ultimate", result.name());
        assertEquals("JetBrains", result.vendor());
        assertEquals(20, result.totalSeats());
        assertEquals(20, result.availableSeats());
        assertEquals("ACTIVE", result.status());

        ArgumentCaptor<SoftwareLicense> captor = ArgumentCaptor.forClass(SoftwareLicense.class);
        verify(licenseRepository).save(captor.capture());
        SoftwareLicense saved = captor.getValue();
        assertEquals("JetBrains", saved.getVendor());
        assertEquals(20, saved.getTotalSeats());
        assertEquals(20, saved.getAvailableSeats());
    }

    @Test
    void createLicense_ZeroSeats_HandlesSafelyWithoutException() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(licenseRepository.save(any(SoftwareLicense.class))).thenAnswer(i -> {
            SoftwareLicense l = i.getArgument(0);
            l.setId(11L);
            return l;
        });

        SoftwareLicenseRequestDTO request = new SoftwareLicenseRequestDTO(
                "Legacy Tool",
                "Acme Corp",
                "KEY-ZERO-0000",
                0,
                null
        );

        SoftwareLicenseDTO result = softwareService.createLicense(request);

        assertNotNull(result);
        assertEquals(0, result.totalSeats());
        assertEquals(0, result.availableSeats());
        assertEquals("PERPETUAL", result.status());
    }

    @Test
    void createLicense_NegativeSeats_ThrowsInvalidOperationException() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicenseRequestDTO request = new SoftwareLicenseRequestDTO(
                "Invalid Tool",
                "Acme",
                "KEY-NEG",
                -5,
                null
        );

        assertThrows(InvalidOperationException.class, () -> softwareService.createLicense(request));
    }

    @Test
    void updateLicense_AllowsIncreasingSeatsAndChangingVendor() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(15L)
                .name("Postman")
                .vendor("OldVendor")
                .licenseKey("KEY-PM-1234")
                .totalSeats(10)
                .availableSeats(8) // 2 allocated
                .build();

        when(licenseRepository.findById(15L)).thenReturn(Optional.of(license));
        when(licenseRepository.save(any(SoftwareLicense.class))).thenAnswer(i -> i.getArgument(0));

        SoftwareLicenseRequestDTO updateReq = new SoftwareLicenseRequestDTO(
                "Postman Enterprise",
                "Postman Inc",
                "KEY-PM-1234",
                25, // increased by 15
                LocalDate.now().plusYears(1)
        );

        SoftwareLicenseDTO updated = softwareService.updateLicense(15L, updateReq);

        assertNotNull(updated);
        assertEquals("Postman Enterprise", updated.name());
        assertEquals("Postman Inc", updated.vendor());
        assertEquals(25, updated.totalSeats());
        assertEquals(23, updated.availableSeats()); // 8 + 15 = 23
    }

    @Test
    void updateLicense_RejectsReducingSeatsBelowAllocatedCount() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(16L)
                .name("Figma")
                .totalSeats(10)
                .availableSeats(4) // 6 allocated
                .build();

        when(licenseRepository.findById(16L)).thenReturn(Optional.of(license));

        SoftwareLicenseRequestDTO updateReq = new SoftwareLicenseRequestDTO(
                "Figma",
                "Figma Inc",
                "KEY-FIGMA",
                5, // trying to set to 5 when 6 are allocated
                null
        );

        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                softwareService.updateLicense(16L, updateReq));

        assertTrue(ex.getMessage().contains("Cannot reduce total seats below currently assigned seats (6)"));
    }

    @Test
    void assignLicense_ValidEmployee_DecrementsAvailableSeats() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(20L)
                .name("Slack Pro")
                .totalSeats(10)
                .availableSeats(5)
                .build();

        when(licenseRepository.findById(20L)).thenReturn(Optional.of(license));
        when(employeeService.find(1L)).thenReturn(activeEmp);
        when(assignmentRepository.existsByLicense_IdAndEmployee_Id(20L, 1L)).thenReturn(false);
        when(assignmentRepository.save(any(SoftwareAssignment.class))).thenAnswer(i -> {
            SoftwareAssignment sa = i.getArgument(0);
            sa.setId(100L);
            return sa;
        });

        SoftwareAssignmentDTO assignment = softwareService.assignLicense(20L, new AssignSoftwareRequestDTO(1L));

        assertNotNull(assignment);
        assertEquals(4, license.getAvailableSeats());
        verify(licenseRepository).save(license);
    }

    @Test
    void assignLicense_WhenZeroAvailableSeats_ThrowsInvalidOperationException() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(21L)
                .name("Zoom Pro")
                .totalSeats(5)
                .availableSeats(0) // No seats left
                .build();

        when(licenseRepository.findById(21L)).thenReturn(Optional.of(license));

        assertThrows(InvalidOperationException.class, () ->
                softwareService.assignLicense(21L, new AssignSoftwareRequestDTO(1L)));
    }

    @Test
    void assignLicense_DuplicateAssignment_ThrowsInvalidOperationException() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(22L)
                .name("GitHub Copilot")
                .totalSeats(10)
                .availableSeats(8)
                .build();

        when(licenseRepository.findById(22L)).thenReturn(Optional.of(license));
        when(employeeService.find(1L)).thenReturn(activeEmp);
        when(assignmentRepository.existsByLicense_IdAndEmployee_Id(22L, 1L)).thenReturn(true);

        InvalidOperationException ex = assertThrows(InvalidOperationException.class, () ->
                softwareService.assignLicense(22L, new AssignSoftwareRequestDTO(1L)));

        assertTrue(ex.getMessage().contains("Employee already has this software license assigned"));
    }

    @Test
    void unassignLicense_IncrementsAvailableSeatsAndDeletesAssignment() {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(23L)
                .name("Docker Desktop")
                .totalSeats(10)
                .availableSeats(3)
                .build();

        SoftwareAssignment assignment = SoftwareAssignment.builder()
                .id(50L)
                .license(license)
                .employee(activeEmp)
                .build();

        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));

        softwareService.unassignLicense(50L);

        assertEquals(4, license.getAvailableSeats());
        verify(licenseRepository).save(license);
        verify(assignmentRepository).delete(assignment);
    }

    @Test
    void expiryClassification_AllFiveStates() {
        LocalDate today = LocalDate.now();

        // 1. Perpetual (null date)
        assertEquals(SoftwareLicenseStatus.PERPETUAL, SoftwareLicenseStatus.fromExpirationDate(null, today));

        // 2. Expired (< today)
        assertEquals(SoftwareLicenseStatus.EXPIRED, SoftwareLicenseStatus.fromExpirationDate(today.minusDays(1), today));
        assertEquals(SoftwareLicenseStatus.EXPIRED, SoftwareLicenseStatus.fromExpirationDate(today.minusDays(13), today));
        assertEquals(SoftwareLicenseStatus.EXPIRED, SoftwareLicenseStatus.fromExpirationDate(today.minusDays(45), today));

        // 3. Expiring soon (0 <= days <= 30, boundaries: 0, 1, 30)
        assertEquals(SoftwareLicenseStatus.EXPIRING_SOON, SoftwareLicenseStatus.fromExpirationDate(today, today)); // 0 days
        assertEquals(SoftwareLicenseStatus.EXPIRING_SOON, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(1), today)); // 1 day
        assertEquals(SoftwareLicenseStatus.EXPIRING_SOON, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(15), today));
        assertEquals(SoftwareLicenseStatus.EXPIRING_SOON, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(30), today)); // 30 days

        // 4. Expiring (31 <= days <= 90, boundaries: 31, 90)
        assertEquals(SoftwareLicenseStatus.EXPIRING, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(31), today)); // 31 days
        assertEquals(SoftwareLicenseStatus.EXPIRING, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(60), today));
        assertEquals(SoftwareLicenseStatus.EXPIRING, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(90), today)); // 90 days

        // 5. Active (> 90 days, boundary: 91)
        assertEquals(SoftwareLicenseStatus.ACTIVE, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(91), today)); // 91 days
        assertEquals(SoftwareLicenseStatus.ACTIVE, SoftwareLicenseStatus.fromExpirationDate(today.plusDays(365), today));
    }

    @Test
    void exportCsv_FormatsExpiredNegativeDaysPerpetualNAAndZeroSeatUtilization() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        LocalDate today = LocalDate.now();

        SoftwareLicense expiredLic = SoftwareLicense.builder()
                .id(31L)
                .name("Expired Tool")
                .vendor("OldVendor")
                .licenseKey("EXPIRED-SECRET-KEY")
                .totalSeats(10)
                .availableSeats(2)
                .expirationDate(today.minusDays(13))
                .build();

        SoftwareLicense perpetualLic = SoftwareLicense.builder()
                .id(32L)
                .name("Perpetual DB")
                .vendor("Oracle")
                .licenseKey("PERPETUAL-SECRET-KEY")
                .totalSeats(5)
                .availableSeats(5)
                .expirationDate(null)
                .build();

        SoftwareLicense zeroSeatLic = SoftwareLicense.builder()
                .id(33L)
                .name("Community Edition")
                .vendor("OpenSource")
                .licenseKey("FREE-KEY")
                .totalSeats(0)
                .availableSeats(0)
                .expirationDate(null)
                .build();

        when(licenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of(expiredLic, perpetualLic, zeroSeatLic));

        byte[] csvBytes = reportService.exportSoftwareUtilizationCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // Expired license: -13 days, EXPIRED status
        assertTrue(csv.contains("Expired Tool"));
        assertTrue(csv.contains("EXPIRED"));
        assertTrue(csv.contains("-13"));

        // Perpetual license: N/A days, PERPETUAL status
        assertTrue(csv.contains("Perpetual DB"));
        assertTrue(csv.contains("PERPETUAL"));
        assertTrue(csv.contains("N/A"));

        // Zero-seat license: 0.0% utilization, 0 seats
        assertTrue(csv.contains("Community Edition"));
        assertTrue(csv.contains("0.0%"));

        // Sensitive keys must NEVER be present
        assertFalse(csv.contains("EXPIRED-SECRET-KEY"));
        assertFalse(csv.contains("PERPETUAL-SECRET-KEY"));
        assertFalse(csv.contains("FREE-KEY"));
    }

    @Test
    void exportCsv_ContainsAllRequiredColumnsAndNeverExposesFullLicenseKey() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(30L)
                .name("IntelliJ IDEA Ultimate")
                .vendor("JetBrains")
                .licenseKey("SUPER-SECRET-LICENSE-KEY-9999")
                .totalSeats(50)
                .availableSeats(10)
                .expirationDate(LocalDate.now().plusDays(20)) // EXPIRING_SOON
                .build();

        when(licenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of(license));

        byte[] csvBytes = reportService.exportSoftwareUtilizationCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Software Name"));
        assertTrue(csv.contains("Vendor"));
        assertTrue(csv.contains("Total Seats"));
        assertTrue(csv.contains("Allocated Seats"));
        assertTrue(csv.contains("Available Seats"));
        assertTrue(csv.contains("Utilization Rate"));
        assertTrue(csv.contains("Expiration Date"));
        assertTrue(csv.contains("License Status"));
        assertTrue(csv.contains("Days Until Expiry"));

        assertTrue(csv.contains("IntelliJ IDEA Ultimate"));
        assertTrue(csv.contains("JetBrains"));
        assertTrue(csv.contains("50"));
        assertTrue(csv.contains("40")); // allocated = 50 - 10
        assertTrue(csv.contains("10")); // available
        assertTrue(csv.contains("80.0%")); // utilization rate
        assertTrue(csv.contains("EXPIRING_SOON"));
        assertTrue(csv.contains("20")); // 20 days until expiry

        // CRITICAL: Full sensitive license key must NEVER be in CSV export
        assertFalse(csv.contains("SUPER-SECRET-LICENSE-KEY-9999"));
    }

    @Test
    void accessControl_NonAdmin_CannotCreateOrUpdateLicense() {
        when(currentUser.isAdmin()).thenReturn(false);

        SoftwareLicenseRequestDTO req = new SoftwareLicenseRequestDTO("Tool", "Vendor", "KEY", 5, null);

        assertThrows(AccessDeniedException.class, () -> softwareService.createLicense(req));
        assertThrows(AccessDeniedException.class, () -> softwareService.updateLicense(1L, req));
        assertThrows(AccessDeniedException.class, () -> softwareService.assignLicense(1L, new AssignSoftwareRequestDTO(1L)));
        assertThrows(AccessDeniedException.class, () -> softwareService.unassignLicense(1L));
    }

    @Test
    void licenseKeyMasking_NonAdminResponse_MasksLicenseKey() {
        when(currentUser.isAdmin()).thenReturn(false);

        SoftwareLicense license = SoftwareLicense.builder()
                .id(40L)
                .name("Adobe CC")
                .vendor("Adobe")
                .licenseKey("ADOBE-CC-PRO-12345-6789")
                .totalSeats(10)
                .availableSeats(10)
                .expirationDate(null)
                .build();

        when(licenseRepository.findById(40L)).thenReturn(Optional.of(license));

        SoftwareLicenseDTO dto = softwareService.getLicense(40L);

        assertNotNull(dto);
        assertEquals("Adobe CC", dto.name());
        assertFalse(dto.licenseKey().contains("ADOBE-CC-PRO-12345-6789"));
        assertTrue(dto.licenseKey().contains("••••"));
        assertTrue(dto.licenseKey().endsWith("6789"));
    }
}
