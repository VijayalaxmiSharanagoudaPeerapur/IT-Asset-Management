package com.itasm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "security_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 100)
    private String eventType; // e.g. "LOGIN_SUCCESS", "LOGIN_FAILED", "ACCOUNT_LOCKED"

    @Column(length = 80)
    private String username;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String details;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
