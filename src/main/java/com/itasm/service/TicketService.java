package com.itasm.service;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.TicketStatus;
import com.itasm.dto.ticket.AssignTicketRequest;
import com.itasm.dto.ticket.TicketRequest;
import com.itasm.dto.ticket.TicketResponse;
import com.itasm.dto.ticket.TicketStatusRequest;
import com.itasm.entity.Asset;
import com.itasm.entity.Employee;
import com.itasm.entity.Notification;
import com.itasm.entity.Ticket;
import com.itasm.entity.UserAccount;
import com.itasm.exception.InvalidOperationException;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.security.CurrentUser;
import com.itasm.domain.Role;
import com.itasm.domain.TicketPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmployeeService employeeService;
    private final AssetService assetService;
    private final Mapper mapper;
    private final CurrentUser currentUser;

    public Page<TicketResponse> search(String q, TicketStatus status, TicketPriority priority, Pageable pageable) {
        if (!currentUser.isAdmin()) {
            return ticketRepository.findByReportedBy_Id(currentUser.requireEmployee().getId(), pageable)
                    .map(mapper::toTicket);
        }
        return ticketRepository.search(q, status, priority, pageable).map(mapper::toTicket);
    }

    public Page<TicketResponse> myTickets(Pageable pageable) {
        return ticketRepository.findByReportedBy_Id(currentUser.requireEmployee().getId(), pageable)
                .map(mapper::toTicket);
    }

    public TicketResponse get(Long id) {
        Ticket ticket = find(id);
        if (!currentUser.isAdmin()) {
            Long myId = currentUser.requireEmployee().getId();
            if (!ticket.getReportedBy().getId().equals(myId)
                    && (ticket.getAssignedTo() == null || !ticket.getAssignedTo().getId().equals(myId))) {
                throw new AccessDeniedException("You can only view your own tickets");
            }
        }
        return mapper.toTicket(ticket);
    }

    @Transactional
    public TicketResponse create(TicketRequest request) {
        Employee reporter;
        if (currentUser.isAdmin() && request.reportedByEmployeeId() != null) {
            reporter = employeeService.find(request.reportedByEmployeeId());
        } else {
            reporter = currentUser.requireEmployee();
        }
        Asset asset = request.assetId() == null ? null : assetService.find(request.assetId());
        long next = ticketRepository.count() + 1;
        
        TicketPriority p = request.priority() != null ? request.priority() : TicketPriority.MEDIUM;
        long resolutionHours = switch (p) {
            case LOW -> 48;
            case MEDIUM -> 24;
            case HIGH -> 8;
            case CRITICAL -> 4;
        };
        long responseHours = Math.max(1, resolutionHours / 4);
        
        Ticket ticket = Ticket.builder()
                .ticketNumber("TCK-" + String.format("%04d", next))
                .title(request.title())
                .description(request.description())
                .priority(p)
                .status(TicketStatus.OPEN)
                .asset(asset)
                .reportedBy(reporter)
                .responseDeadline(Instant.now().plus(responseHours, java.time.temporal.ChronoUnit.HOURS))
                .resolutionDeadline(Instant.now().plus(resolutionHours, java.time.temporal.ChronoUnit.HOURS))
                .build();
        ticketRepository.save(ticket);
        notifyAdmins("New ticket " + ticket.getTicketNumber() + " (" + ticket.getPriority() + "): " + ticket.getTitle(), ticket);
        if (reporter.getUser() != null) {
            notify(reporter.getUser(), "Your ticket " + ticket.getTicketNumber() + " was created and is OPEN.", ticket);
        }
        return mapper.toTicket(ticket);
    }

    @Transactional
    public TicketResponse changeStatus(Long id, TicketStatusRequest request) {
        requireAdmin();
        Ticket ticket = find(id);
        TicketStatus from = ticket.getStatus();
        TicketStatus to = request.status();
        validateTransition(from, to);
        ticket.setStatus(to);
        ticket.setResolutionNotes(request.resolutionNotes());
        if (to == TicketStatus.RESOLVED || to == TicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());
            Instant deadline = ticket.getEffectiveResolutionDeadline();
            if (deadline != null && ticket.getResolvedAt().isAfter(deadline)) {
                ticket.setSlaBreached(true);
            }
            if (ticket.getAsset() != null && ticket.getAsset().getStatus() == AssetStatus.REPAIR) {
                Asset asset = ticket.getAsset();
                if (asset.getAssignedEmployee() != null) {
                    asset.setStatus(AssetStatus.ASSIGNED);
                } else {
                    asset.setStatus(AssetStatus.AVAILABLE);
                }
            }
        }
        notify(ticket.getReportedBy().getUser(),
                "Ticket " + ticket.getTicketNumber() + " status changed: " + from + " → " + to, ticket);
        return mapper.toTicket(ticket);
    }

    @Transactional
    public TicketResponse assign(Long id, AssignTicketRequest request) {
        requireAdmin();
        Ticket ticket = find(id);
        Employee assignee = employeeService.find(request.employeeId());
        ticket.setAssignedTo(assignee);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        if (assignee.getUser() != null) {
            notify(assignee.getUser(), "Ticket " + ticket.getTicketNumber() + " was assigned to you.", ticket);
        }
        notify(ticket.getReportedBy().getUser(),
                "Ticket " + ticket.getTicketNumber() + " is being handled by " + assignee.getFullName(), ticket);
        return mapper.toTicket(ticket);
    }

    private void validateTransition(TicketStatus from, TicketStatus to) {
        boolean ok = switch (from) {
            case OPEN -> to == TicketStatus.IN_PROGRESS || to == TicketStatus.CLOSED;
            case IN_PROGRESS -> to == TicketStatus.RESOLVED || to == TicketStatus.OPEN;
            case RESOLVED -> to == TicketStatus.CLOSED || to == TicketStatus.OPEN;
            case CLOSED -> false;
        };
        if (!ok) {
            throw new InvalidOperationException("Invalid ticket status change: " + from + " → " + to);
        }
    }

    public Ticket find(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    private void notifyAdmins(String message, Ticket ticket) {
        List<UserAccount> admins = userAccountRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isEnabled())
                .toList();
        admins.forEach(admin -> notify(admin, message, ticket));
    }

    private void notify(UserAccount user, String message, Ticket ticket) {
        if (user == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(user)
                .message(message)
                .ticket(ticket)
                .readFlag(false)
                .build());
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin/IT support can update tickets");
        }
    }

    public Instant monthStart() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
