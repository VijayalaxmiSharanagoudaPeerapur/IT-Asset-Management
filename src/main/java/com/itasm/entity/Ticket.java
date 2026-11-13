package com.itasm.entity;

import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String ticketNumber;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_id")
    private Employee reportedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private Employee assignedTo;

    @Column(length = 1000)
    private String resolutionNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private Instant resolvedAt;

    private Instant responseDeadline;

    private Instant resolutionDeadline;

    @Column(nullable = false)
    @Builder.Default
    private boolean slaBreached = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean slaWarningSent = false;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = TicketStatus.OPEN;
        }
        if (priority == null) {
            priority = TicketPriority.MEDIUM;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Instant getEffectiveResolutionDeadline() {
        if (resolutionDeadline != null) {
            return resolutionDeadline;
        }
        if (createdAt != null) {
            long hours = switch (priority != null ? priority : TicketPriority.MEDIUM) {
                case LOW -> 48;
                case MEDIUM -> 24;
                case HIGH -> 8;
                case CRITICAL -> 4;
            };
            return createdAt.plus(hours, java.time.temporal.ChronoUnit.HOURS);
        }
        return null;
    }

    public String getSlaStatus() {
        if (status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED) {
            return "RESOLVED";
        }
        if (slaBreached) {
            return "BREACHED";
        }
        Instant deadline = getEffectiveResolutionDeadline();
        if (deadline == null) {
            return "NO_DEADLINE";
        }
        Instant now = Instant.now();
        if (now.isAfter(deadline)) {
            return "BREACHED";
        }
        if (deadline.minusSeconds(7200).isBefore(now)) {
            return "AT_RISK";
        }
        return "ON_TRACK";
    }
}
