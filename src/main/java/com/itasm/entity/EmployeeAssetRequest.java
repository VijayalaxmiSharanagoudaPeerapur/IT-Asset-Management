package com.itasm.entity;

import com.itasm.domain.RequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "employee_asset_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAssetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, length = 200)
    private String requestedItem;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(length = 500)
    private String adminNotes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = RequestStatus.PENDING;
        }
    }
}
