package com.itasm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String employeeCode;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(length = 80)
    private String department;

    @Column(length = 80)
    private String designation;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private UserAccount user;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
