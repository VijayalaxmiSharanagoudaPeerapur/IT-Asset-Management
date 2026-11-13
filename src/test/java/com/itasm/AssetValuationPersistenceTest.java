package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.dto.asset.AssetRequest;
import com.itasm.dto.asset.AssetResponse;
import com.itasm.dto.report.ManagementReportResponse;
import com.itasm.entity.Asset;
import com.itasm.entity.AssetHistory;
import com.itasm.repository.AssetHistoryRepository;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.AssetService;
import com.itasm.service.EmployeeService;
import com.itasm.service.Mapper;
import com.itasm.service.ReportService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetValuationPersistenceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetHistoryRepository historyRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private SoftwareLicenseRepository softwareLicenseRepository;
    @Mock
    private CurrentUser currentUser;

    private Mapper mapper;
    private AssetService assetService;
    private ReportService reportService;
    private Validator validator;

    @BeforeEach
    void setUp() {
        mapper = new Mapper();
        assetService = new AssetService(assetRepository, historyRepository, employeeService, mapper, currentUser);
        reportService = new ReportService(assetRepository, ticketRepository, softwareLicenseRepository, currentUser);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void createAsset_WithExplicitPrice_PersistsBigDecimalPrice() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset toSave = invocation.getArgument(0);
            toSave.setId(100L);
            return toSave;
        });

        BigDecimal expectedPrice = new BigDecimal("65000.00");
        AssetRequest request = new AssetRequest(
                "LAP-901",
                "SN-DELL-901",
                "Laptop",
                "Dell",
                "Latitude 5540",
                LocalDate.of(2025, 1, 10),
                LocalDate.of(2028, 1, 10),
                expectedPrice,
                AssetStatus.AVAILABLE,
                "Brand new developer laptop"
        );

        AssetResponse response = assetService.create(request);

        assertNotNull(response);
        assertEquals(expectedPrice, response.price());

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset saved = captor.getValue();
        assertEquals("LAP-901", saved.getAssetTag());
        assertEquals(expectedPrice, saved.getPrice());
        verify(historyRepository).save(any(AssetHistory.class));
    }

    @Test
    void createAsset_WithNullPrice_PersistsNullPriceWithoutZeroDefault() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.save(any(Asset.class))).thenAnswer(invocation -> {
            Asset toSave = invocation.getArgument(0);
            toSave.setId(101L);
            return toSave;
        });

        AssetRequest request = new AssetRequest(
                "MON-902",
                "SN-HP-902",
                "Monitor",
                "HP",
                "E24 G5",
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2028, 2, 1),
                null, // Price omitted / optional
                AssetStatus.AVAILABLE,
                "Monitor without recorded purchase price"
        );

        AssetResponse response = assetService.create(request);

        assertNotNull(response);
        assertNull(response.price(), "Unpriced asset response must have null price, not fabricated 0.00");

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository).save(captor.capture());
        Asset saved = captor.getValue();
        assertNull(saved.getPrice(), "Unpriced asset entity must persist null price in database");
    }

    @Test
    void updateAsset_WithNewPrice_UpdatesAndPersistsCorrectly() {
        when(currentUser.isAdmin()).thenReturn(true);

        Asset existingAsset = Asset.builder()
                .id(102L)
                .assetTag("LAP-102")
                .serialNumber("SN-102")
                .type("Laptop")
                .brand("Dell")
                .model("Latitude 5440")
                .status(AssetStatus.AVAILABLE)
                .price(null) // originally unpriced
                .build();

        when(assetRepository.findById(102L)).thenReturn(Optional.of(existingAsset));

        BigDecimal updatedPrice = new BigDecimal("72500.50");
        AssetRequest updateRequest = new AssetRequest(
                "LAP-102",
                "SN-102",
                "Laptop",
                "Dell",
                "Latitude 5440",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2027, 6, 1),
                updatedPrice,
                AssetStatus.AVAILABLE,
                "Price retroactively verified and recorded"
        );

        AssetResponse response = assetService.update(102L, updateRequest);

        assertNotNull(response);
        assertEquals(updatedPrice, response.price());
        assertEquals(updatedPrice, existingAsset.getPrice());
        verify(historyRepository).save(any(AssetHistory.class));
    }

    @Test
    void updateAsset_PreservesPrice_WhenExistingPriceSupplied() {
        when(currentUser.isAdmin()).thenReturn(true);

        BigDecimal currentPrice = new BigDecimal("55000.00");
        Asset existingAsset = Asset.builder()
                .id(103L)
                .assetTag("SRV-103")
                .serialNumber("SN-103")
                .type("Server")
                .brand("Dell")
                .model("PowerEdge")
                .status(AssetStatus.AVAILABLE)
                .price(currentPrice)
                .build();

        when(assetRepository.findById(103L)).thenReturn(Optional.of(existingAsset));

        AssetRequest updateRequest = new AssetRequest(
                "SRV-103",
                "SN-103",
                "Server",
                "Dell",
                "PowerEdge R740",
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 1, 1),
                currentPrice, // Supplied as populated in edit form
                AssetStatus.AVAILABLE,
                "Updated server notes only"
        );

        AssetResponse response = assetService.update(103L, updateRequest);

        assertNotNull(response);
        assertEquals(currentPrice, response.price());
        assertEquals(currentPrice, existingAsset.getPrice());
    }

    @Test
    void validation_RejectsNegativePrice() {
        AssetRequest request = new AssetRequest(
                "LAP-999",
                "SN-999",
                "Laptop",
                "Dell",
                "Latitude",
                LocalDate.now(),
                LocalDate.now().plusYears(3),
                new BigDecimal("-50.00"),
                AssetStatus.AVAILABLE,
                "Invalid negative price"
        );

        Set<ConstraintViolation<AssetRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("price")
                && v.getMessage().contains("Purchase price must be greater than or equal to 0.00")));
    }

    @Test
    void validation_RejectsInvalidScaleAndPrecision() {
        // More than 2 decimal digits
        AssetRequest invalidScale = new AssetRequest(
                "LAP-998",
                "SN-998",
                "Laptop",
                "Dell",
                "Latitude",
                LocalDate.now(),
                LocalDate.now().plusYears(3),
                new BigDecimal("100.999"),
                AssetStatus.AVAILABLE,
                "Invalid scale"
        );

        Set<ConstraintViolation<AssetRequest>> violationsScale = validator.validate(invalidScale);
        assertFalse(violationsScale.isEmpty());
        assertTrue(violationsScale.stream().anyMatch(v -> v.getPropertyPath().toString().equals("price")));

        // Exceeding 8 integer digits
        AssetRequest invalidDigits = new AssetRequest(
                "LAP-997",
                "SN-997",
                "Laptop",
                "Dell",
                "Latitude",
                LocalDate.now(),
                LocalDate.now().plusYears(3),
                new BigDecimal("100000000.00"),
                AssetStatus.AVAILABLE,
                "Exceeds max integer digits"
        );

        Set<ConstraintViolation<AssetRequest>> violationsDigits = validator.validate(invalidDigits);
        assertFalse(violationsDigits.isEmpty());
        assertTrue(violationsDigits.stream().anyMatch(v -> v.getPropertyPath().toString().equals("price")));
    }

    @Test
    void validation_AcceptsZeroPriceAndNullPrice() {
        // Zero price is valid
        AssetRequest zeroPriceReq = new AssetRequest(
                "LAP-001",
                "SN-001",
                "Laptop",
                "Dell",
                "Latitude",
                LocalDate.now(),
                LocalDate.now().plusYears(3),
                new BigDecimal("0.00"),
                AssetStatus.AVAILABLE,
                "Zero price donation"
        );

        Set<ConstraintViolation<AssetRequest>> zeroViolations = validator.validate(zeroPriceReq);
        assertTrue(zeroViolations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("price")));

        // Null price is valid (optional)
        AssetRequest nullPriceReq = new AssetRequest(
                "LAP-002",
                "SN-002",
                "Laptop",
                "Dell",
                "Latitude",
                LocalDate.now(),
                LocalDate.now().plusYears(3),
                null,
                AssetStatus.AVAILABLE,
                "Omitted price"
        );

        Set<ConstraintViolation<AssetRequest>> nullViolations = validator.validate(nullPriceReq);
        assertTrue(nullViolations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("price")));
    }

    @Test
    void valuationCalculations_AccuratelySumsAndAveragesPricedAssetsOnly() {
        when(currentUser.isAdmin()).thenReturn(true);

        Asset a1 = Asset.builder().id(1L).assetTag("A1").status(AssetStatus.ASSIGNED).price(new BigDecimal("50000.00")).build();
        Asset a2 = Asset.builder().id(2L).assetTag("A2").status(AssetStatus.AVAILABLE).price(new BigDecimal("70000.00")).build();
        Asset a3 = Asset.builder().id(3L).assetTag("A3").status(AssetStatus.REPAIR).price(null).build(); // Unpriced

        when(assetRepository.findAll()).thenReturn(List.of(a1, a2, a3));
        when(ticketRepository.findAll()).thenReturn(List.of());
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        ManagementReportResponse report = reportService.getManagementReport();

        assertEquals(3, report.assetSummary().totalAssets());
        assertEquals(2, report.assetSummary().pricedAssetsCount());
        assertEquals(1, report.assetSummary().unpricedAssetsCount());
        assertTrue(report.assetSummary().valuationAvailable());
        assertEquals(new BigDecimal("120000.00"), report.assetSummary().totalValuation());
        // Average across priced assets only: ₹120,000 / 2 = ₹60,000.00
        assertEquals(new BigDecimal("60000.00"), report.assetSummary().averageValue());
    }

    @Test
    void exportAssetValuationCsv_ExportsCorrectFormattingAndNotSpecified() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        Asset priced = Asset.builder()
                .id(1L)
                .assetTag("AST-01")
                .serialNumber("SN-01")
                .type("Laptop")
                .brand("Dell")
                .model("Latitude")
                .status(AssetStatus.ASSIGNED)
                .price(new BigDecimal("65000.00"))
                .riskScore(10)
                .build();

        Asset unpriced = Asset.builder()
                .id(2L)
                .assetTag("AST-02")
                .serialNumber("SN-02")
                .type("Monitor")
                .brand("HP")
                .model("E24")
                .status(AssetStatus.AVAILABLE)
                .price(null)
                .riskScore(5)
                .build();

        when(assetRepository.findAll()).thenReturn(List.of(priced, unpriced));

        byte[] csvBytes = reportService.exportAssetValuationCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Asset Tag"));
        assertTrue(csv.contains("Price"));
        assertTrue(csv.contains("AST-01"));
        assertTrue(csv.contains("₹65000.00"));
        assertTrue(csv.contains("AST-02"));
        assertTrue(csv.contains("Not Specified"));
        assertFalse(csv.contains("₹0.00"), "Unpriced asset must never be exported as ₹0.00");
    }
}
