package com.itasm;

import com.itasm.domain.Role;
import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.dto.ticket.TicketRequest;
import com.itasm.dto.ticket.TicketResponse;
import com.itasm.entity.Asset;
import com.itasm.entity.Employee;
import com.itasm.entity.Notification;
import com.itasm.entity.Ticket;
import com.itasm.entity.UserAccount;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.security.CurrentUser;
import com.itasm.service.AssetService;
import com.itasm.service.EmployeeService;
import com.itasm.service.Mapper;
import com.itasm.service.TicketScheduledTasks;
import com.itasm.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaAutomationTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private AssetService assetService;
    @Mock
    private Mapper mapper;
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private TicketService ticketService;

    private TicketScheduledTasks ticketScheduledTasks;

    private Employee employee;
    private UserAccount adminUser;

    @BeforeEach
    void setUp() {
        ticketScheduledTasks = new TicketScheduledTasks(ticketRepository, notificationRepository, userAccountRepository);

        employee = Employee.builder()
                .id(1L)
                .firstName("Jane")
                .lastName("Doe")
                .employeeCode("EMP-001")
                .department("IT")
                .active(true)
                .build();

        adminUser = UserAccount.builder()
                .id(99L)
                .username("admin")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void createTicket_SetsCorrectDeadlinesForCriticalPriority() {
        when(currentUser.requireEmployee()).thenReturn(employee);
        when(ticketRepository.count()).thenReturn(5L);

        TicketRequest request = new TicketRequest(
                "System Outage",
                "Production server down",
                TicketPriority.CRITICAL,
                null,
                null
        );

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(ticketCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toTicket(any())).thenReturn(mock(TicketResponse.class));

        ticketService.create(request);

        Ticket savedTicket = ticketCaptor.getValue();
        assertNotNull(savedTicket);
        assertEquals("TCK-0006", savedTicket.getTicketNumber());
        assertEquals(TicketPriority.CRITICAL, savedTicket.getPriority());
        assertNotNull(savedTicket.getResponseDeadline());
        assertNotNull(savedTicket.getResolutionDeadline());

        // CRITICAL resolution is 4 hours, response is 1 hour
        long responseDiffHours = ChronoUnit.HOURS.between(Instant.now(), savedTicket.getResponseDeadline());
        long resolutionDiffHours = ChronoUnit.HOURS.between(Instant.now(), savedTicket.getResolutionDeadline());
        assertTrue(responseDiffHours >= 0 && responseDiffHours <= 1);
        assertTrue(resolutionDiffHours >= 3 && resolutionDiffHours <= 4);
    }

    @Test
    void ticket_GetSlaStatus_CalculatesStateTransitions() {
        Ticket ticket = Ticket.builder()
                .ticketNumber("TCK-0001")
                .status(TicketStatus.OPEN)
                .resolutionDeadline(Instant.now().plus(5, ChronoUnit.HOURS))
                .slaBreached(false)
                .build();

        // 5 hours away -> ON_TRACK
        assertEquals("ON_TRACK", ticket.getSlaStatus());

        // Less than 2 hours away -> AT_RISK
        ticket.setResolutionDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
        assertEquals("AT_RISK", ticket.getSlaStatus());

        // Past deadline -> BREACHED
        ticket.setResolutionDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        assertEquals("BREACHED", ticket.getSlaStatus());

        // Resolved ticket -> RESOLVED
        ticket.setStatus(TicketStatus.RESOLVED);
        assertEquals("RESOLVED", ticket.getSlaStatus());
    }

    @Test
    void checkSlaBreaches_FlagsBreachedTicketsAndNotifiesAdmin() {
        Ticket pastDeadlineTicket = Ticket.builder()
                .id(10L)
                .ticketNumber("TCK-PAST")
                .priority(TicketPriority.HIGH)
                .status(TicketStatus.OPEN)
                .resolutionDeadline(Instant.now().minus(30, ChronoUnit.MINUTES))
                .slaBreached(false)
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(pastDeadlineTicket));
        when(userAccountRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

        ticketScheduledTasks.checkSlaBreaches();

        assertTrue(pastDeadlineTicket.isSlaBreached());
        verify(ticketRepository).save(pastDeadlineTicket);
        verify(notificationRepository).save(argThat(notif ->
                notif.getMessage().contains("ESCALATION: SLA Breached for Ticket TCK-PAST")
        ));
    }

    @Test
    void checkSlaBreaches_SendsWarningForAtRiskTickets() {
        Ticket atRiskTicket = Ticket.builder()
                .id(20L)
                .ticketNumber("TCK-RISK")
                .priority(TicketPriority.MEDIUM)
                .status(TicketStatus.IN_PROGRESS)
                .resolutionDeadline(Instant.now().plus(45, ChronoUnit.MINUTES)) // within 2 hours
                .slaBreached(false)
                .slaWarningSent(false)
                .build();

        when(ticketRepository.findAll()).thenReturn(List.of(atRiskTicket));
        when(userAccountRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

        ticketScheduledTasks.checkSlaBreaches();

        assertTrue(atRiskTicket.isSlaWarningSent());
        verify(ticketRepository).save(atRiskTicket);
        verify(notificationRepository).save(argThat(notif ->
                notif.getMessage().contains("WARNING: Ticket TCK-RISK is at risk of SLA breach")
        ));
    }
}
