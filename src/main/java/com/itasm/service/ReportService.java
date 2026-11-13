package com.itasm.service;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.SoftwareLicenseStatus;
import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.dto.report.*;
import com.itasm.entity.Asset;
import com.itasm.entity.SoftwareLicense;
import com.itasm.entity.Ticket;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final AssetRepository assetRepository;
    private final TicketRepository ticketRepository;
    private final SoftwareLicenseRepository softwareLicenseRepository;
    private final CurrentUser currentUser;

    public ManagementReportResponse getManagementReport() {
        requireAdmin();

        List<Asset> assets = assetRepository.findAll();
        List<Ticket> tickets = ticketRepository.findAll();
        List<SoftwareLicense> licenses = softwareLicenseRepository.findAllByOrderByNameAsc();

        // 1. Asset & Valuation Summary
        long totalAssets = assets.size();
        long pricedAssetsCount = assets.stream().filter(a -> a.getPrice() != null).count();
        long unpricedAssetsCount = totalAssets - pricedAssetsCount;
        boolean valuationAvailable = pricedAssetsCount > 0;

        BigDecimal totalValuation = assets.stream()
                .map(Asset::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageValue = pricedAssetsCount > 0
                ? totalValuation.divide(BigDecimal.valueOf(pricedAssetsCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Long> assetsByStatus = new LinkedHashMap<>();
        for (AssetStatus status : AssetStatus.values()) {
            long count = assets.stream().filter(a -> a.getStatus() == status).count();
            assetsByStatus.put(status.name(), count);
        }

        Map<String, Long> assetsByType = assets.stream()
                .collect(Collectors.groupingBy(a -> a.getType() != null ? a.getType() : "Unknown", Collectors.counting()));

        Map<String, Long> assetsByDepartment = assets.stream()
                .collect(Collectors.groupingBy(a -> {
                    if (a.getAssignedEmployee() == null) return "Unassigned Assets";
                    return a.getAssignedEmployee().getDepartment() != null && !a.getAssignedEmployee().getDepartment().isBlank()
                            ? a.getAssignedEmployee().getDepartment()
                            : "General";
                }, Collectors.counting()));

        AssetSummaryReport assetSummary = new AssetSummaryReport(
                totalAssets, pricedAssetsCount, unpricedAssetsCount, valuationAvailable,
                totalValuation, averageValue, assetsByStatus, assetsByType, assetsByDepartment
        );

        // 2. Ticket & SLA Summary
        long totalTickets = tickets.size();
        long openTickets = tickets.stream().filter(t -> t.getStatus() == TicketStatus.OPEN).count();
        long inProgressTickets = tickets.stream().filter(t -> t.getStatus() == TicketStatus.IN_PROGRESS).count();
        long resolvedTickets = tickets.stream().filter(t -> t.getStatus() == TicketStatus.RESOLVED).count();
        long closedTickets = tickets.stream().filter(t -> t.getStatus() == TicketStatus.CLOSED).count();
        long slaBreachedTickets = tickets.stream().filter(t -> t.isSlaBreached() || "BREACHED".equals(t.getSlaStatus())).count();

        double slaBreachRate = totalTickets > 0
                ? Math.round((slaBreachedTickets * 100.0 / totalTickets) * 100.0) / 100.0
                : 0.0;

        Map<String, Long> ticketsByPriority = new LinkedHashMap<>();
        for (TicketPriority priority : TicketPriority.values()) {
            long count = tickets.stream().filter(t -> t.getPriority() == priority).count();
            ticketsByPriority.put(priority.name(), count);
        }

        TicketSummaryReport ticketSummary = new TicketSummaryReport(
                totalTickets, openTickets, inProgressTickets, resolvedTickets, closedTickets,
                slaBreachedTickets, slaBreachRate, ticketsByPriority
        );

        // 3. Software Licenses Utilization
        long totalLicenses = licenses.size();
        long totalSeats = licenses.stream().mapToLong(SoftwareLicense::getTotalSeats).sum();
        long availableSeats = licenses.stream().mapToLong(SoftwareLicense::getAvailableSeats).sum();
        long allocatedSeats = Math.max(0, totalSeats - availableSeats);
        double overallUtilization = totalSeats > 0
                ? Math.round((allocatedSeats * 100.0 / totalSeats) * 100.0) / 100.0
                : 0.0;

        List<SoftwareLicenseUtilization> topLicenses = licenses.stream().map(l -> {
            int allocated = Math.max(0, l.getTotalSeats() - l.getAvailableSeats());
            double rate = l.getTotalSeats() > 0
                    ? Math.round((allocated * 100.0 / l.getTotalSeats()) * 100.0) / 100.0
                    : 0.0;
            return new SoftwareLicenseUtilization(
                    l.getName(),
                    l.getTotalSeats(),
                    allocated,
                    l.getAvailableSeats(),
                    rate,
                    l.getExpirationDate() != null ? l.getExpirationDate().toString() : "Perpetual"
            );
        }).toList();

        SoftwareSummaryReport softwareSummary = new SoftwareSummaryReport(
                totalLicenses, totalSeats, allocatedSeats, availableSeats, overallUtilization, topLicenses
        );

        // 4. Warranty Summary
        LocalDate today = LocalDate.now();
        LocalDate in30 = today.plusDays(30);
        LocalDate in90 = today.plusDays(90);

        long expiredCount = assets.stream()
                .filter(a -> a.getWarrantyExpiry() != null && a.getWarrantyExpiry().isBefore(today)).count();
        long expiringIn30 = assets.stream()
                .filter(a -> a.getWarrantyExpiry() != null && !a.getWarrantyExpiry().isBefore(today) && !a.getWarrantyExpiry().isAfter(in30)).count();
        long expiringIn90 = assets.stream()
                .filter(a -> a.getWarrantyExpiry() != null && a.getWarrantyExpiry().isAfter(in30) && !a.getWarrantyExpiry().isAfter(in90)).count();
        long activeWarranties = assets.stream()
                .filter(a -> a.getWarrantyExpiry() != null && a.getWarrantyExpiry().isAfter(in90)).count();

        WarrantySummaryReport warrantySummary = new WarrantySummaryReport(
                expiredCount, expiringIn30, expiringIn90, activeWarranties
        );

        // 5. Risk Summary
        long highRiskCount = assets.stream().filter(a -> a.getRiskScore() >= 30).count();
        long criticalRiskCount = assets.stream().filter(a -> a.getRiskScore() >= 70).count();

        List<HighRiskAssetSummary> topRisk = assets.stream()
                .filter(a -> a.getRiskScore() > 0)
                .sorted(Comparator.comparingInt(Asset::getRiskScore).reversed())
                .limit(10)
                .map(a -> new HighRiskAssetSummary(
                        a.getId(),
                        a.getAssetTag(),
                        (a.getBrand() != null ? a.getBrand() + " " : "") + a.getModel(),
                        a.getStatus().name(),
                        a.getRiskScore(),
                        a.getAssignedEmployee() != null ? a.getAssignedEmployee().getFullName() : "Unassigned"
                ))
                .toList();

        RiskSummaryReport riskSummary = new RiskSummaryReport(
                highRiskCount, criticalRiskCount, topRisk
        );

        return new ManagementReportResponse(
                assetSummary, ticketSummary, softwareSummary, warrantySummary, riskSummary, Instant.now()
        );
    }

    public byte[] exportAssetValuationCsv() throws Exception {
        requireAdmin();
        List<Asset> assets = assetRepository.findAll();

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Asset Tag", "Serial Number", "Type", "Brand", "Model", "Status",
                        "Price", "Assigned Employee", "Department", "Purchase Date", "Warranty Expiry", "Risk Score")
                .build())) {
            for (Asset a : assets) {
                printer.printRecord(
                        com.itasm.util.CsvSanitizer.sanitize(a.getAssetTag()),
                        com.itasm.util.CsvSanitizer.sanitize(a.getSerialNumber()),
                        com.itasm.util.CsvSanitizer.sanitize(a.getType()),
                        com.itasm.util.CsvSanitizer.sanitize(a.getBrand()),
                        com.itasm.util.CsvSanitizer.sanitize(a.getModel()),
                        com.itasm.util.CsvSanitizer.sanitize(a.getStatus()),
                        a.getPrice() != null ? "₹" + a.getPrice().toString() : "Not Specified",
                        com.itasm.util.CsvSanitizer.sanitize(a.getAssignedEmployee() != null ? a.getAssignedEmployee().getFullName() : "None"),
                        com.itasm.util.CsvSanitizer.sanitize(a.getAssignedEmployee() != null ? a.getAssignedEmployee().getDepartment() : "None"),
                        a.getPurchaseDate() != null ? a.getPurchaseDate().toString() : "N/A",
                        a.getWarrantyExpiry() != null ? a.getWarrantyExpiry().toString() : "N/A",
                        a.getRiskScore()
                );
            }
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportTicketsSlaCsv() throws Exception {
        requireAdmin();
        List<Ticket> tickets = ticketRepository.findAll();

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Ticket Number", "Title", "Priority", "Status", "Reported By",
                        "Assigned To", "Created At", "Resolution Deadline", "Resolved At",
                        "Resolution Time (Hours)", "SLA Status", "SLA Breached")
                .build())) {
            for (Ticket t : tickets) {
                String resolutionDeadline = t.getEffectiveResolutionDeadline() != null
                        ? t.getEffectiveResolutionDeadline().toString()
                        : "N/A";

                String resolutionTime;
                if (t.getResolvedAt() != null && t.getCreatedAt() != null) {
                    java.time.Duration d = java.time.Duration.between(t.getCreatedAt(), t.getResolvedAt());
                    double hours = Math.round((d.toMinutes() / 60.0) * 10.0) / 10.0;
                    resolutionTime = String.format("%.1fh", hours);
                } else {
                    resolutionTime = (t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED) ? "N/A" : "Pending";
                }

                printer.printRecord(
                        com.itasm.util.CsvSanitizer.sanitize(t.getTicketNumber()),
                        com.itasm.util.CsvSanitizer.sanitize(t.getTitle()),
                        com.itasm.util.CsvSanitizer.sanitize(t.getPriority()),
                        com.itasm.util.CsvSanitizer.sanitize(t.getStatus()),
                        com.itasm.util.CsvSanitizer.sanitize(t.getReportedBy() != null ? t.getReportedBy().getFullName() : "N/A"),
                        com.itasm.util.CsvSanitizer.sanitize(t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : "Unassigned"),
                        t.getCreatedAt() != null ? t.getCreatedAt().toString() : "N/A",
                        resolutionDeadline,
                        t.getResolvedAt() != null ? t.getResolvedAt().toString() : "Unresolved",
                        resolutionTime,
                        t.getSlaStatus(),
                        t.isSlaBreached() ? "YES" : "NO"
                );
            }
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportSoftwareUtilizationCsv() throws Exception {
        requireAdmin();
        List<SoftwareLicense> licenses = softwareLicenseRepository.findAllByOrderByNameAsc();
        LocalDate today = LocalDate.now();

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("Software Name", "Vendor", "Total Seats", "Allocated Seats", "Available Seats", "Utilization Rate", "Expiration Date", "License Status", "Days Until Expiry")
                .build())) {
            for (SoftwareLicense l : licenses) {
                int allocated = Math.max(0, l.getTotalSeats() - l.getAvailableSeats());
                double rate = l.getTotalSeats() > 0 ? (allocated * 100.0 / l.getTotalSeats()) : 0.0;
                SoftwareLicenseStatus status = SoftwareLicenseStatus.fromExpirationDate(l.getExpirationDate(), today);
                String daysUntilExpiry;
                if (l.getExpirationDate() == null) {
                    daysUntilExpiry = "N/A";
                } else {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(today, l.getExpirationDate());
                    daysUntilExpiry = String.valueOf(days);
                }

                printer.printRecord(
                        com.itasm.util.CsvSanitizer.sanitize(l.getName()),
                        com.itasm.util.CsvSanitizer.sanitize(l.getVendor() != null && !l.getVendor().isBlank() ? l.getVendor() : "Not Specified"),
                        l.getTotalSeats(),
                        allocated,
                        l.getAvailableSeats(),
                        String.format("%.1f%%", rate),
                        l.getExpirationDate() != null ? l.getExpirationDate().toString() : "Perpetual",
                        status.name(),
                        daysUntilExpiry
                );
            }
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can access management reports");
        }
    }
}
