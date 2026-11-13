package com.itasm.controller;

import com.itasm.dto.report.ManagementReportResponse;
import com.itasm.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Report Management", description = "Executive management and operational reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/management")
    @Operation(summary = "Get full executive management report (Admin only)")
    public ManagementReportResponse managementReport() {
        return reportService.getManagementReport();
    }

    @GetMapping(value = {"/assets-valuation/export", "/asset-valuation/export"}, produces = "text/csv")
    @Operation(summary = "Export asset financial valuation report to CSV (Admin only)")
    public ResponseEntity<byte[]> exportAssetValuation() throws Exception {
        byte[] csv = reportService.exportAssetValuationCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asset-valuation-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping(value = "/tickets-sla/export", produces = "text/csv")
    @Operation(summary = "Export tickets and SLA breach report to CSV (Admin only)")
    public ResponseEntity<byte[]> exportTicketsSla() throws Exception {
        byte[] csv = reportService.exportTicketsSlaCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tickets-sla-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping(value = "/software-licenses/export", produces = "text/csv")
    @Operation(summary = "Export software license utilization report to CSV (Admin only)")
    public ResponseEntity<byte[]> exportSoftwareUtilization() throws Exception {
        byte[] csv = reportService.exportSoftwareUtilizationCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"software-licenses-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
