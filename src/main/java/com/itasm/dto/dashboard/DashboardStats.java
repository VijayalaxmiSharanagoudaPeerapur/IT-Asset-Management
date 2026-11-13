package com.itasm.dto.dashboard;

public record DashboardStats(
        long totalAssets,
        long assignedAssets,
        long availableAssets,
        long repairAssets,
        long retiredAssets,
        long totalEmployees,
        long activeEmployees,
        long openTickets,
        long inProgressTickets,
        long highPriorityOpen,
        long resolvedThisMonth,
        long unreadNotifications
) {
}
