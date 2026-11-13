package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.Role;
import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.dto.ticket.TicketStatusRequest;
import com.itasm.entity.Asset;
import com.itasm.entity.Employee;
import com.itasm.entity.Ticket;
import com.itasm.entity.UserAccount;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.SoftwareLicenseRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.AssetService;
import com.itasm.service.EmployeeService;
import com.itasm.service.Mapper;
import com.itasm.service.ReportService;
import com.itasm.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketSlaReportingTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private SoftwareLicenseRepository softwareLicenseRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private AssetService assetService;
    @Mock
    private CurrentUser currentUser;

    private Mapper mapper;
    private TicketService ticketService;
    private ReportService reportService;

    private Employee reporter;
    private UserAccount adminUser;

    @BeforeEach
    void setUp() {
        mapper = new Mapper();
        ticketService = new TicketService(ticketRepository, notificationRepository, userAccountRepository,
                employeeService, assetService, mapper, currentUser);
        reportService = new ReportService(assetRepository, ticketRepository, softwareLicenseRepository, currentUser);

        adminUser = UserAccount.builder().id(1L).username("admin").role(Role.ADMIN).build();
        reporter = Employee.builder().id(2L).firstName("Vijay").lastName("Kumar").user(adminUser).build();
    }

    @Test
    void getSlaStatus_CalculatesFallbackDeadline_WhenResolutionDeadlineNull() {
        Instant created = Instant.now().minus(5, ChronoUnit.HOURS);

        // 1. CRITICAL priority (4 hour SLA) created 5 hours ago -> Overdue / BREACHED
        Ticket criticalTicket = Ticket.builder()
                .ticketNumber("TCK-001")
                .priority(TicketPriority.CRITICAL)
                .status(TicketStatus.OPEN)
                .createdAt(created)
                .resolutionDeadline(null) // null deadline triggers fallback
                .build();

        assertEquals("BREACHED", criticalTicket.getSlaStatus());

        // 2. LOW priority (48 hour SLA) created 5 hours ago -> ON_TRACK
        Ticket lowTicket = Ticket.builder()
                .ticketNumber("TCK-002")
                .priority(TicketPriority.LOW)
                .status(TicketStatus.OPEN)
                .createdAt(created)
                .resolutionDeadline(null)
                .build();

        assertEquals("ON_TRACK", lowTicket.getSlaStatus());
        assertFalse(lowTicket.isSlaBreached());

        // 3. HIGH priority (8 hour SLA) created 7 hours ago -> within 2 hours -> AT_RISK
        Ticket highTicket = Ticket.builder()
                .ticketNumber("TCK-003")
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.OPEN)
                .createdAt(Instant.now().minus(7, ChronoUnit.HOURS))
                .resolutionDeadline(null)
                .build();

        assertEquals("AT_RISK", highTicket.getSlaStatus());
    }

    @Test
    void getSlaStatus_ReturnsNoDeadline_WhenBothDeadlinesAndCreatedAtNull() {
        Ticket uninitializedTicket = Ticket.builder()
                .ticketNumber("TCK-000")
                .status(TicketStatus.OPEN)
                .resolutionDeadline(null)
                .createdAt(null)
                .build();

        assertEquals("NO_DEADLINE", uninitializedTicket.getSlaStatus());
    }

    @Test
    void changeStatus_FlagsLateResolution_AsSlaBreached() {
        when(currentUser.isAdmin()).thenReturn(true);

        // Ticket deadline was 2 hours ago
        Instant deadline = Instant.now().minus(2, ChronoUnit.HOURS);
        Ticket lateTicket = Ticket.builder()
                .id(101L)
                .ticketNumber("TCK-LATE")
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.HIGH)
                .reportedBy(reporter)
                .resolutionDeadline(deadline)
                .slaBreached(false) // Initially false before resolution
                .build();

        when(ticketRepository.findById(101L)).thenReturn(Optional.of(lateTicket));

        // Admin resolves ticket past deadline
        ticketService.changeStatus(101L, new TicketStatusRequest(TicketStatus.RESOLVED, "Fixed after delay"));

        assertEquals(TicketStatus.RESOLVED, lateTicket.getStatus());
        assertNotNull(lateTicket.getResolvedAt());
        assertTrue(lateTicket.isSlaBreached(), "Resolving ticket past SLA deadline must set slaBreached to true");
        assertEquals("RESOLVED", lateTicket.getSlaStatus());
    }

    @Test
    void changeStatus_PreservesOnTimeResolution_NotBreached() {
        when(currentUser.isAdmin()).thenReturn(true);

        // Ticket deadline is 5 hours in future
        Instant deadline = Instant.now().plus(5, ChronoUnit.HOURS);
        Ticket onTimeTicket = Ticket.builder()
                .id(102L)
                .ticketNumber("TCK-ONTIME")
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.MEDIUM)
                .reportedBy(reporter)
                .resolutionDeadline(deadline)
                .slaBreached(false)
                .build();

        when(ticketRepository.findById(102L)).thenReturn(Optional.of(onTimeTicket));

        ticketService.changeStatus(102L, new TicketStatusRequest(TicketStatus.RESOLVED, "Fixed quickly"));

        assertEquals(TicketStatus.RESOLVED, onTimeTicket.getStatus());
        assertNotNull(onTimeTicket.getResolvedAt());
        assertFalse(onTimeTicket.isSlaBreached(), "Resolving ticket before SLA deadline must not breach SLA");
    }

    @Test
    void exportTicketsSlaCsv_OutputsResolutionDeadlineAndResolutionTime() throws Exception {
        when(currentUser.isAdmin()).thenReturn(true);

        Instant created = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant deadline = created.plus(4, ChronoUnit.HOURS);
        Instant resolved = created.plus(2, ChronoUnit.HOURS); // Took 2 hours to resolve

        Ticket resolvedTicket = Ticket.builder()
                .ticketNumber("TCK-201")
                .title("Wi-Fi issue")
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.RESOLVED)
                .reportedBy(reporter)
                .createdAt(created)
                .resolutionDeadline(deadline)
                .resolvedAt(resolved)
                .slaBreached(false)
                .build();

        Ticket openTicket = Ticket.builder()
                .ticketNumber("TCK-202")
                .title("New Monitor Setup")
                .priority(TicketPriority.LOW)
                .status(TicketStatus.OPEN)
                .reportedBy(reporter)
                .createdAt(created)
                .resolutionDeadline(deadline)
                .resolvedAt(null)
                .slaBreached(false)
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(resolvedTicket, openTicket));

        byte[] csvBytes = reportService.exportTicketsSlaCsv();
        assertNotNull(csvBytes);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Resolution Deadline"));
        assertTrue(csv.contains("Resolution Time (Hours)"));
        assertTrue(csv.contains("TCK-201"));
        assertTrue(csv.contains("2.0h"), "Resolved ticket should calculate 2.0h resolution time");
        assertTrue(csv.contains("TCK-202"));
        assertTrue(csv.contains("Pending"), "Open ticket should display Pending resolution time");
    }
}
