package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.entity.Asset;
import com.itasm.entity.SoftwareLicense;
import com.itasm.entity.Ticket;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.ReportService;
import com.itasm.util.CsvSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvFormulaInjectionTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private SoftwareLicenseRepository softwareLicenseRepository;
    @Mock
    private CurrentUser currentUser;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(assetRepository, ticketRepository, softwareLicenseRepository, currentUser);
    }

    @Test
    void csvSanitizer_NeutralizesDangerousCharacters() {
        // Starts with =
        assertEquals("'=cmd|' /C calc'!A0", CsvSanitizer.sanitize("=cmd|' /C calc'!A0"));

        // Starts with +
        assertEquals("'+1+2", CsvSanitizer.sanitize("+1+2"));

        // Starts with -
        assertEquals("'-2+3+cmd", CsvSanitizer.sanitize("-2+3+cmd"));

        // Starts with @
        assertEquals("'@SUM(1,2)", CsvSanitizer.sanitize("@SUM(1,2)"));

        // Starts with tab \t
        assertEquals("'\tcalc", CsvSanitizer.sanitize("\tcalc"));

        // Starts with carriage return \r
        assertEquals("'\rcalc", CsvSanitizer.sanitize("\rcalc"));

        // Leading whitespace followed by dangerous character
        assertEquals("'   =SUM(A1:A5)", CsvSanitizer.sanitize("   =SUM(A1:A5)"));

        // Safe benign text
        assertEquals("MacBook Pro", CsvSanitizer.sanitize("MacBook Pro"));
        assertEquals("Dell Latitude", CsvSanitizer.sanitize("Dell Latitude"));
        assertEquals("AST-001", CsvSanitizer.sanitize("AST-001"));
        assertEquals("", CsvSanitizer.sanitize(""));
        assertEquals("", CsvSanitizer.sanitize(null));

        // Valid numeric values and dates preserved
        assertEquals("-13", CsvSanitizer.sanitize(-13));
        assertEquals("50", CsvSanitizer.sanitize(50L));
        assertEquals("45.5", CsvSanitizer.sanitize(45.5));
        assertEquals("1000.00", CsvSanitizer.sanitize(new BigDecimal("1000.00")));
        assertEquals("2026-10-14", CsvSanitizer.sanitize(LocalDate.of(2026, 10, 14)));
        assertEquals("true", CsvSanitizer.sanitize(Boolean.TRUE));
    }

    @Test
    void exportAssetValuationCsv_SanitizesFormulaInjectionPayloads() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        Asset maliciousAsset = Asset.builder()
                .id(1L)
                .assetTag("=cmd|' /C calc'!A0")
                .serialNumber("+123456789")
                .type("@AdminType")
                .brand("-DangerousBrand")
                .model("=1+1")
                .status(AssetStatus.AVAILABLE)
                .price(new BigDecimal("50000.00"))
                .build();

        when(assetRepository.findAll()).thenReturn(List.of(maliciousAsset));

        byte[] csvBytes = reportService.exportAssetValuationCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("'=cmd|' /C calc'!A0"));
        assertTrue(csv.contains("'+123456789"));
        assertTrue(csv.contains("'@AdminType"));
        assertTrue(csv.contains("'-DangerousBrand"));
        assertTrue(csv.contains("'=1+1"));
    }

    @Test
    void exportTicketsSlaCsv_SanitizesFormulaInjectionPayloads() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        Ticket maliciousTicket = Ticket.builder()
                .id(1L)
                .ticketNumber("=HYPERLINK(\"http://evil.com\")")
                .title("@SUM(A1:A10)")
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.OPEN)
                .createdAt(Instant.now())
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(maliciousTicket));

        byte[] csvBytes = reportService.exportTicketsSlaCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("'=HYPERLINK"));
        assertTrue(csv.contains("http://evil.com"));
        assertTrue(csv.contains("'@SUM(A1:A10)"));
    }

    @Test
    void exportSoftwareUtilizationCsv_SanitizesFormulaInjectionPayloads() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        SoftwareLicense maliciousLicense = SoftwareLicense.builder()
                .id(1L)
                .name("=maliciousFormula()")
                .vendor("+EvilVendor")
                .licenseKey("SECRET-KEY-123")
                .totalSeats(10)
                .availableSeats(5)
                .expirationDate(LocalDate.now().plusMonths(6))
                .build();

        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of(maliciousLicense));

        byte[] csvBytes = reportService.exportSoftwareUtilizationCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("'=maliciousFormula()"));
        assertTrue(csv.contains("'+EvilVendor"));
    }
}
