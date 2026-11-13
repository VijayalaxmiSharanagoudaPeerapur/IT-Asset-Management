package com.itasm.security;

import com.itasm.entity.Employee;
import com.itasm.entity.UserAccount;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.EmployeeRepository;
import com.itasm.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;

    public UserAccount requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResourceNotFoundException("Not authenticated");
        }
        return userAccountRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public boolean isAdmin() {
        return requireUser().getRole().name().equals("ADMIN");
    }

    public Employee requireEmployee() {
        return employeeRepository.findByUser_Id(requireUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile linked to this account"));
    }
}
