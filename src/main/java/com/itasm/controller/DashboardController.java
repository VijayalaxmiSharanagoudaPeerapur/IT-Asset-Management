package com.itasm.controller;

import com.itasm.dto.PageResponse;
import com.itasm.dto.dashboard.DashboardStats;
import com.itasm.dto.notification.NotificationResponse;
import com.itasm.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "5. Admin Dashboard & Notifications")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/dashboard/stats")
    @Operation(summary = "Asset, ticket, and employee overview counts")
    public DashboardStats stats() {
        return dashboardService.stats();
    }

    @GetMapping("/api/dashboard/analytics")
    @Operation(summary = "Advanced analytics for charts")
    public java.util.Map<String, Object> advancedAnalytics() {
        return dashboardService.advancedAnalytics();
    }

    @GetMapping("/api/dashboard/performance")
    @Operation(summary = "Real measured performance metrics and SLA benchmarks (Admin only)")
    public com.itasm.dto.dashboard.PerformanceMetricsResponse performance() {
        return dashboardService.performanceMetrics();
    }

    @GetMapping({"/api/notifications", "/api/notifications/me"})
    public PageResponse<NotificationResponse> notifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(dashboardService.myNotifications(PageRequest.of(page, size)));
    }

    @PostMapping("/api/notifications/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return dashboardService.markRead(id);
    }
}
