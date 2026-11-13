package com.itasm.dto.employee;

public record EmployeeResponse(
        Long id,
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        String department,
        String designation,
        String phone,
        boolean active,
        String username
) {
}
