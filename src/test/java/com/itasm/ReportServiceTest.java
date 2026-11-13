package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.dto.report.ManagementReportResponse;
import com.itasm.entity.Asset;
import com.itasm.entity.Employee;
import com.itasm.entity.SoftwareLicense;
import com.itasm.entity.Ticket;
import com.itasm.exception.InvalidOperationException;
import org.springframework.security.access.AccessDeniedException;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private SoftwareLicenseRepository softwareLicenseRepository;
    @Mock
    private CurrentUser currentUser;

    private ReportService reportService;

    private Asset asset1;
    private Asset asset2;
    private Ticket ticket1;
    private Ticket ticket2;
    private SoftwareLicense license1;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(assetRepository, ticketRepository, softwareLicenseRepository, currentUser);

        Employee engineeringEmp = Employee.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP-001")
                .department("Engineering")
                .active(true)
                .build();

        asset1 = Asset.builder()
                .id(101L)
                .assetTag("AST-ENG-01")
                .serialNumber("SN-APPLE-101")
                .brand("Apple")
                .model("MacBook Pro 16 M2 Max")
                .type("Laptop")
                .status(AssetStatus.ASSIGNED)
                .price(new BigDecimal("2500.00"))
                .assignedEmployee(engineeringEmp)
                .warrantyExpiry(LocalDate.now().plusMonths(6))
                .riskScore(20)
                .build();

        asset2 = Asset.builder()
                .id(102L)
                .assetTag("AST-SRV-02")
                .serialNumber("SN-DELL-102")
                .brand("Dell")
                .model("PowerEdge R740")
                .type("Server")
                .status(AssetStatus.REPAIR)
                .price(new BigDecimal("5000.00"))
                .assignedEmployee(null)
                .warrantyExpiry(LocalDate.now().minusDays(10))
                .riskScore(85)
                .build();

        ticket1 = Ticket.builder()
                .id(201L)
                .ticketNumber("TCK-201")
                .title("Database server failure")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.CRITICAL)
                .asset(asset2)
                .slaBreached(true)
                .build();

        ticket2 = Ticket.builder()
                .id(202L)
                .ticketNumber("TCK-202")
                .title("Keyboard replacement")
                .status(TicketStatus.RESOLVED)
                .priority(TicketPriority.LOW)
                .asset(asset1)
                .slaBreached(false)
                .build();

        license1 = SoftwareLicense.builder()
                .id(301L)
                .name("IntelliJ IDEA Ultimate")
                .totalSeats(50)
                .availableSeats(10)
                .expirationDate(LocalDate.now().plusMonths(12))
                .build();
    }

    @Test
    void getManagementReport_SuccessfulAggregation() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.findAll()).thenReturn(List.of(asset1, asset2));
        when(ticketRepository.findAll()).thenReturn(List.of(ticket1, ticket2));
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of(license1));

        ManagementReportResponse report = reportService.getManagementReport();

        assertNotNull(report);
        assertNotNull(report.generatedAt());

        // Asset verification
        assertEquals(2, report.assetSummary().totalAssets());
        assertEquals(2, report.assetSummary().pricedAssetsCount());
        assertEquals(0, report.assetSummary().unpricedAssetsCount());
        assertTrue(report.assetSummary().valuationAvailable());
        assertEquals(new BigDecimal("7500.00"), report.assetSummary().totalValuation());
        assertEquals(new BigDecimal("3750.00"), report.assetSummary().averageValue());
        assertEquals(1L, report.assetSummary().assetsByDepartment().get("Engineering"));
        assertEquals(1L, report.assetSummary().assetsByDepartment().get("Unassigned Assets"));
        assertEquals(1L, report.assetSummary().assetsByStatus().get("ASSIGNED"));
        assertEquals(1L, report.assetSummary().assetsByStatus().get("REPAIR"));

        // Ticket verification
        assertEquals(2, report.ticketSummary().totalTickets());
        assertEquals(1, report.ticketSummary().openTickets());
        assertEquals(1, report.ticketSummary().resolvedTickets());
        assertEquals(1, report.ticketSummary().slaBreachedTickets());
        assertEquals(50.0, report.ticketSummary().slaBreachRatePercent());
        assertEquals(1L, report.ticketSummary().ticketsByPriority().get("CRITICAL"));
        assertEquals(1L, report.ticketSummary().ticketsByPriority().get("LOW"));

        // Software license verification
        assertEquals(1, report.softwareSummary().totalLicenses());
        assertEquals(50, report.softwareSummary().totalSeats());
        assertEquals(40, report.softwareSummary().allocatedSeats());
        assertEquals(10, report.softwareSummary().availableSeats());
        assertEquals(80.0, report.softwareSummary().overallUtilizationRate());

        // Warranty verification
        assertEquals(1, report.warrantySummary().expiredCount());
        assertEquals(1, report.warrantySummary().activeWarrantyCount());

        // Risk verification
        assertEquals(1, report.riskSummary().highRiskCount()); // asset2 score is 85 >= 30
        assertEquals(1, report.riskSummary().criticalRiskCount()); // asset2 score is 85 >= 70
        assertFalse(report.riskSummary().topHighRiskAssets().isEmpty());
        assertEquals("AST-SRV-02", report.riskSummary().topHighRiskAssets().get(0).assetTag());
    }

    @Test
    void getManagementReport_MissingPrices_IndicatesUnavailableValuation() {
        when(currentUser.isAdmin()).thenReturn(true);
        Asset unpricedAsset1 = Asset.builder()
                .id(1L)
                .assetTag("L102")
                .status(AssetStatus.ASSIGNED)
                .price(null)
                .build();
        Asset unpricedAsset2 = Asset.builder()
                .id(2L)
                .assetTag("L105")
                .status(AssetStatus.AVAILABLE)
                .price(null)
                .build();

        when(assetRepository.findAll()).thenReturn(List.of(unpricedAsset1, unpricedAsset2));
        when(ticketRepository.findAll()).thenReturn(List.of());
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        ManagementReportResponse report = reportService.getManagementReport();

        assertEquals(2, report.assetSummary().totalAssets());
        assertEquals(0, report.assetSummary().pricedAssetsCount());
        assertEquals(2, report.assetSummary().unpricedAssetsCount());
        assertFalse(report.assetSummary().valuationAvailable());
        assertEquals(BigDecimal.ZERO, report.assetSummary().totalValuation());
        assertEquals(BigDecimal.ZERO, report.assetSummary().averageValue());
    }

    @Test
    void getManagementReport_PartialPrices_ComputesAverageAcrossPricedAssetsOnly() {
        when(currentUser.isAdmin()).thenReturn(true);
        Asset priced = Asset.builder()
                .id(1L)
                .assetTag("P1")
                .status(AssetStatus.ASSIGNED)
                .price(new BigDecimal("10000.00"))
                .build();
        Asset unpriced = Asset.builder()
                .id(2L)
                .assetTag("U1")
                .status(AssetStatus.AVAILABLE)
                .price(null)
                .build();

        when(assetRepository.findAll()).thenReturn(List.of(priced, unpriced));
        when(ticketRepository.findAll()).thenReturn(List.of());
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        ManagementReportResponse report = reportService.getManagementReport();

        assertEquals(2, report.assetSummary().totalAssets());
        assertEquals(1, report.assetSummary().pricedAssetsCount());
        assertEquals(1, report.assetSummary().unpricedAssetsCount());
        assertTrue(report.assetSummary().valuationAvailable());
        assertEquals(new BigDecimal("10000.00"), report.assetSummary().totalValuation());
        // Average across priced assets only: ₹10,000 / 1 = ₹10,000.00
        assertEquals(new BigDecimal("10000.00"), report.assetSummary().averageValue());
    }

    @Test
    void getManagementReport_EmptySoftwareLicenses_PreservesCleanEmptyState() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.findAll()).thenReturn(List.of(asset1));
        when(ticketRepository.findAll()).thenReturn(List.of());
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        ManagementReportResponse report = reportService.getManagementReport();

        assertEquals(0, report.softwareSummary().totalLicenses());
        assertEquals(0, report.softwareSummary().totalSeats());
        assertEquals(0, report.softwareSummary().allocatedSeats());
        assertEquals(0, report.softwareSummary().availableSeats());
        assertEquals(0.0, report.softwareSummary().overallUtilizationRate());
        assertTrue(report.softwareSummary().topLicenses().isEmpty());
    }

    @Test
    void getManagementReport_Unauthorized_ThrowsException() {
        when(currentUser.isAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                reportService.getManagementReport());

        assertTrue(ex.getMessage().contains("Only admin"));
    }

    @Test
    void exportAssetValuationCsv_ProducesValidCsv() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.findAll()).thenReturn(List.of(asset1, asset2));

        byte[] csvBytes = reportService.exportAssetValuationCsv();

        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Asset Tag"));
        assertTrue(csv.contains("Price"));
        assertTrue(csv.contains("AST-ENG-01"));
        assertTrue(csv.contains("₹2500.00"));
        assertTrue(csv.contains("Engineering"));
        assertTrue(csv.contains("Dell"));
    }

    @Test
    void exportAssetValuationCsv_UnpricedAsset_OutputsNotSpecified() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        Asset unpriced = Asset.builder()
                .id(1L)
                .assetTag("L102")
                .serialNumber("SN-102")
                .type("Laptop")
                .brand("Dell")
                .model("Latitude")
                .status(AssetStatus.ASSIGNED)
                .price(null)
                .build();
        when(assetRepository.findAll()).thenReturn(List.of(unpriced));

        byte[] csvBytes = reportService.exportAssetValuationCsv();
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("L102"));
        assertTrue(csv.contains("Not Specified"));
    }

    @Test
    void exportTicketsSlaCsv_ProducesValidCsv() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(ticketRepository.findAll()).thenReturn(List.of(ticket1, ticket2));

        byte[] csvBytes = reportService.exportTicketsSlaCsv();

        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Ticket Number"));
        assertTrue(csv.contains("SLA Status"));
        assertTrue(csv.contains("TCK-201"));
        assertTrue(csv.contains("BREACHED"));
        assertTrue(csv.contains("CRITICAL"));
    }

    @Test
    void exportSoftwareUtilizationCsv_ProducesValidCsv() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of(license1));

        byte[] csvBytes = reportService.exportSoftwareUtilizationCsv();

        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Software Name"));
        assertTrue(csv.contains("Utilization Rate"));
        assertTrue(csv.contains("IntelliJ IDEA Ultimate"));
        assertTrue(csv.contains("50"));
        assertTrue(csv.contains("40"));
    }

    @Test
    void exportSoftwareUtilizationCsv_EmptyLicenses_ProducesHeaderOnly() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        byte[] csvBytes = reportService.exportSoftwareUtilizationCsv();

        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csv.contains("Software Name"));
        assertTrue(csv.contains("Utilization Rate"));
    }
}
