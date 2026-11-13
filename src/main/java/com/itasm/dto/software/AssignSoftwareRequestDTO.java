package com.itasm.dto.software;

import jakarta.validation.constraints.NotNull;

public record AssignSoftwareRequestDTO(
        @NotNull Long employeeId
) {}
