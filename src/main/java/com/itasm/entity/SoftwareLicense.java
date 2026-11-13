package com.itasm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "software_licenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String vendor;

    @Column(length = 200)
    private String licenseKey;

    private int totalSeats;
    
    private int availableSeats;

    private LocalDate expirationDate;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        if (totalSeats < 0) {
            totalSeats = 0;
        }
        if (availableSeats < 0) {
            availableSeats = 0;
        }
        if (availableSeats > totalSeats) {
            availableSeats = totalSeats;
        }
    }
}
