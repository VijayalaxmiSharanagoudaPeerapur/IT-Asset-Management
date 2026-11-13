package com.itasm.entity;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.HistoryAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "asset_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssetStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AssetStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id")
    private UserAccount performedBy;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
