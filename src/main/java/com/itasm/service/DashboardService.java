package com.itasm.service;

import com.itasm.dto.dashboard.DashboardStats;
import com.itasm.dto.notification.NotificationResponse;
import com.itasm.entity.Notification;
import com.itasm.entity.UserAccount;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.domain.AssetStatus;
import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.EmployeeRepository;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;
    private final TicketService ticketService;
    private final CurrentUser currentUser;
    private final Mapper mapper;
    private final PerformanceMetricsService performanceMetricsService;

    public DashboardStats stats() {
        UserAccount user = currentUser.requireUser();
        Instant from = ticketService.monthStart();
        return new DashboardStats(
                assetRepository.count(),
                assetRepository.countByStatus(AssetStatus.ASSIGNED),
                assetRepository.countByStatus(AssetStatus.AVAILABLE),
                assetRepository.countByStatus(AssetStatus.REPAIR),
                assetRepository.countByStatus(AssetStatus.RETIRED),
                employeeRepository.count(),
                employeeRepository.countByActiveTrue(),
                ticketRepository.countByStatus(TicketStatus.OPEN),
                ticketRepository.countByStatus(TicketStatus.IN_PROGRESS),
                ticketRepository.countByPriorityAndStatusNot(TicketPriority.HIGH, TicketStatus.CLOSED)
                        + ticketRepository.countByPriorityAndStatusNot(TicketPriority.CRITICAL, TicketStatus.CLOSED),
                ticketRepository.countByStatusAndResolvedAtBetween(TicketStatus.RESOLVED, from, Instant.now())
                        + ticketRepository.countByStatusAndResolvedAtBetween(TicketStatus.CLOSED, from, Instant.now()),
                notificationRepository.countByUser_IdAndReadFlagFalse(user.getId())
        );
    }

    public Page<NotificationResponse> myNotifications(Pageable pageable) {
        return notificationRepository
                .findByUser_IdOrderByCreatedAtDesc(currentUser.requireUser().getId(), pageable)
                .map(mapper::toNotification);
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(currentUser.requireUser().getId())) {
            throw new ResourceNotFoundException("Notification not found");
        }
        n.setReadFlag(true);
        return mapper.toNotification(n);
    }

    public Map<String, Object> advancedAnalytics() {
        Map<String, Object> analytics = new java.util.HashMap<>();
        
        // Asset status distribution
        Map<String, Long> assetStatusDist = new java.util.HashMap<>();
        for (AssetStatus status : AssetStatus.values()) {
            assetStatusDist.put(status.name(), assetRepository.countByStatus(status));
        }
        analytics.put("assetStatusDistribution", assetStatusDist);
        
        // Ticket priority distribution
        Map<String, Long> ticketPriorityDist = new java.util.HashMap<>();
        for (TicketPriority priority : TicketPriority.values()) {
            ticketPriorityDist.put(priority.name(), ticketRepository.countByPriority(priority));
        }
        analytics.put("ticketPriorityDistribution", ticketPriorityDist);
        
        return analytics;
    }

    public com.itasm.dto.dashboard.PerformanceMetricsResponse performanceMetrics() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can view performance metrics");
        }
        return performanceMetricsService.getPerformanceMetrics();
    }
}
