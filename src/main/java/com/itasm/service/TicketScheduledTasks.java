package com.itasm.service;

import com.itasm.domain.Role;
import com.itasm.domain.TicketStatus;
import com.itasm.entity.Notification;
import com.itasm.entity.Ticket;
import com.itasm.entity.UserAccount;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketScheduledTasks {

    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;

    @Scheduled(fixedRate = 900000) // Every 15 minutes
    @Transactional
    public void checkSlaBreaches() {
        log.info("Running scheduled task to check for SLA breaches");
        
        List<Ticket> breachedTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED && t.getStatus() != TicketStatus.RESOLVED)
                .filter(t -> !t.isSlaBreached())
                .filter(t -> (t.getResolutionDeadline() != null && Instant.now().isAfter(t.getResolutionDeadline())) || 
                             (t.getResponseDeadline() != null && t.getStatus() == TicketStatus.OPEN && Instant.now().isAfter(t.getResponseDeadline())))
                .toList();

        List<UserAccount> admins = userAccountRepository.findByRole(Role.ADMIN);
        
        for (Ticket ticket : breachedTickets) {
            ticket.setSlaBreached(true);
            ticketRepository.save(ticket);
            
            String message = String.format("ESCALATION: SLA Breached for Ticket %s (%s)", ticket.getTicketNumber(), ticket.getPriority());
            
            for (UserAccount admin : admins) {
                notificationRepository.save(Notification.builder()
                        .user(admin)
                        .message(message)
                        .readFlag(false)
                        .ticket(ticket)
                        .build());
            }
        }

        List<Ticket> atRiskTickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED && t.getStatus() != TicketStatus.RESOLVED)
                .filter(t -> !t.isSlaBreached() && !t.isSlaWarningSent())
                .filter(t -> t.getResolutionDeadline() != null && t.getResolutionDeadline().minusSeconds(7200).isBefore(Instant.now()))
                .toList();

        for (Ticket ticket : atRiskTickets) {
            ticket.setSlaWarningSent(true);
            ticketRepository.save(ticket);
            
            String message = String.format("WARNING: Ticket %s is at risk of SLA breach.", ticket.getTicketNumber());
            
            for (UserAccount admin : admins) {
                notificationRepository.save(Notification.builder()
                        .user(admin)
                        .message(message)
                        .readFlag(false)
                        .ticket(ticket)
                        .build());
            }
        }
        
        log.info("Flagged {} tickets for SLA breach, {} for SLA warning", breachedTickets.size(), atRiskTickets.size());
    }
}
