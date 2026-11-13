package com.itasm.dto.auth;

import com.itasm.domain.Role;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String username,
        Role role,
        Long employeeId,
        String fullName
) {
}
