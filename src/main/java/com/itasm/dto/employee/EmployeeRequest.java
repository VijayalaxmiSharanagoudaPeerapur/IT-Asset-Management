package com.itasm.dto.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployeeRequest(
        @NotBlank @Size(max = 40) String employeeCode,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email String email,
        String department,
        String designation,
        String phone,
        @NotBlank @Size(min = 6, max = 80) String username,
        @Size(min = 6, max = 80) String password
) {
}
