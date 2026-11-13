package com.itasm.dto.asset;

import com.itasm.domain.AssetStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetRequest(
        @NotBlank String assetTag,
        @NotBlank String serialNumber,
        @NotBlank String type,
        String brand,
        @NotBlank String model,
        LocalDate purchaseDate,
        LocalDate warrantyExpiry,
        @DecimalMin(value = "0.00", message = "Purchase price must be greater than or equal to 0.00")
        @Digits(integer = 8, fraction = 2, message = "Purchase price must have at most 8 integer digits and 2 decimals")
        BigDecimal price,
        @NotNull AssetStatus status,
        String notes
) {
}
