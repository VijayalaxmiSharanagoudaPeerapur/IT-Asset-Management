package com.itasm;

import com.itasm.dto.dashboard.EndpointMetric;
import com.itasm.dto.dashboard.PerformanceMetricsResponse;
import com.itasm.service.PerformanceMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMetricsTest {

    private PerformanceMetricsService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceMetricsService();
    }

    @Test
    void testInitialMetrics() {
        PerformanceMetricsResponse res = service.getPerformanceMetrics();
        assertNotNull(res);
        assertEquals(0, res.totalRequests());
        assertEquals(0, res.totalErrors());
        assertEquals(0.0, res.errorRatePercent());
        assertEquals(0.0, res.measuredAvgResponseTimeMs());
        assertEquals("<200ms", res.targetResponseTime());
        assertEquals("99.9%", res.targetUptime());
        assertTrue(res.uptimeSeconds() >= 0);
        assertNotNull(res.uptimeFormatted());
        assertTrue(res.topEndpoints().isEmpty());
    }

    @Test
    void testRecordRequestsAndCalculateMetrics() {
        // Record 3 requests:
        // 1. GET /api/assets -> 200 OK, 10ms
        service.recordRequest("GET", "/api/assets", 200, 10_000_000L);
        // 2. POST /api/assets -> 201 Created, 20ms
        service.recordRequest("POST", "/api/assets", 201, 20_000_000L);
        // 3. GET /api/assets/45 -> 404 Not Found, 30ms (should normalize to /api/assets/{id})
        service.recordRequest("GET", "/api/assets/45", 404, 30_000_000L);

        PerformanceMetricsResponse res = service.getPerformanceMetrics();

        assertEquals(3, res.totalRequests());
        assertEquals(1, res.totalErrors());
        assertEquals(33.33, res.errorRatePercent());
        assertEquals(20.0, res.measuredAvgResponseTimeMs());

        // Check top endpoints
        assertEquals(3, res.topEndpoints().size());

        EndpointMetric notFoundMetric = res.topEndpoints().stream()
                .filter(e -> e.endpoint().equals("/api/assets/{id}"))
                .findFirst()
                .orElse(null);

        assertNotNull(notFoundMetric);
        assertEquals("GET", notFoundMetric.method());
        assertEquals(1, notFoundMetric.requests());
        assertEquals(1, notFoundMetric.errors());
        assertEquals(30.0, notFoundMetric.avgResponseTimeMs());
    }

    @Test
    void testTargetSlaNotFalsifiedAsMeasured() {
        PerformanceMetricsResponse res = service.getPerformanceMetrics();
        // Explicitly check that target SLA strings are distinct and preserved
        assertEquals("<200ms", res.targetResponseTime());
        assertEquals("99.9%", res.targetUptime());
        // Measured response time starts at 0.0 or actual calculation, never hardcoded to <200ms
        assertEquals(0.0, res.measuredAvgResponseTimeMs());
    }
}
