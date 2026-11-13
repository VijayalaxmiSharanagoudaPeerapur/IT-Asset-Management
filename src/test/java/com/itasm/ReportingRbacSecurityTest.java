package com.itasm;

import com.itasm.repository.AssetRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingRbacSecurityTest {

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
    void nonAdmin_CannotAccessManagementReport() {
        when(currentUser.isAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reportService.getManagementReport());
        assertTrue(ex.getMessage().contains("Only admin"));
    }

    @Test
    void nonAdmin_CannotExportAssetValuationCsv() {
        when(currentUser.isAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reportService.exportAssetValuationCsv());
        assertTrue(ex.getMessage().contains("Only admin"));
    }

    @Test
    void nonAdmin_CannotExportTicketsSlaCsv() {
        when(currentUser.isAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reportService.exportTicketsSlaCsv());
        assertTrue(ex.getMessage().contains("Only admin"));
    }

    @Test
    void nonAdmin_CannotExportSoftwareUtilizationCsv() {
        when(currentUser.isAdmin()).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> reportService.exportSoftwareUtilizationCsv());
        assertTrue(ex.getMessage().contains("Only admin"));
    }

    @Test
    void admin_CanAccessAllReportsSuccessfully() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);
        when(assetRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.findAll()).thenReturn(List.of());
        when(softwareLicenseRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        assertNotNull(reportService.getManagementReport());
        assertNotNull(reportService.exportAssetValuationCsv());
        assertNotNull(reportService.exportTicketsSlaCsv());
        assertNotNull(reportService.exportSoftwareUtilizationCsv());
    }
}
