package com.itasm.dto.software;

import java.time.LocalDate;

public record SoftwareLicenseDTO(
        Long id,
        String name,
        String vendor,
        String licenseKey,
        int totalSeats,
        int availableSeats,
        LocalDate expirationDate,
        String status,
        Long daysUntilExpiry
) {
    public SoftwareLicenseDTO(Long id, String name, String licenseKey, int totalSeats, int availableSeats, LocalDate expirationDate) {
        this(id, name, null, licenseKey, totalSeats, availableSeats, expirationDate, null, null);
    }
}
