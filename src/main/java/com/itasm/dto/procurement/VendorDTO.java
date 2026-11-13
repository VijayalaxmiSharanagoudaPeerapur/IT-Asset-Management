package com.itasm.dto.procurement;

import java.time.Instant;

public record VendorDTO(
        Long id,
        String name,
        String contactPerson,
        String email,
        String phone,
        String address,
        boolean active,
        Instant createdAt
) {}
