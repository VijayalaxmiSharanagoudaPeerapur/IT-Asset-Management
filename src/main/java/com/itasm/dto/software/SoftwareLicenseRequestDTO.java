package com.itasm.dto.software;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record SoftwareLicenseRequestDTO(
        @NotBlank String name,
        String vendor,
        String licenseKey,
        @Min(value = 0, message = "Total seats cannot be negative") int totalSeats,
        LocalDate expirationDate
) {
    public SoftwareLicenseRequestDTO(String name, String licenseKey, int totalSeats, LocalDate expirationDate) {
        this(name, null, licenseKey, totalSeats, expirationDate);
    }
}
