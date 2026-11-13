package com.itasm.entity;

import com.itasm.domain.AssetStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String assetTag;

    @Column(nullable = false, unique = true, length = 80)
    private String serialNumber;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 80)
    private String brand;

    @Column(nullable = false, length = 120)
    private String model;

    private LocalDate purchaseDate;

    private LocalDate warrantyExpiry;
    
    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    private Employee assignedEmployee;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private int riskScore = 0;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = AssetStatus.AVAILABLE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
