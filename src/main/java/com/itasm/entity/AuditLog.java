package com.itasm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String entityType; // e.g., "Employee", "Ticket", "SoftwareLicense"

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 30)
    private String action; // e.g., "CREATE", "UPDATE", "DELETE", "ASSIGN"

    @Column(length = 2000)
    private String oldValue;

    @Column(length = 2000)
    private String newValue;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "performed_by_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "employee"})
    private UserAccount performedBy;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
