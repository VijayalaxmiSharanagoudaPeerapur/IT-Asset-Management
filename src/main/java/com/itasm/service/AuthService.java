package com.itasm.service;

import com.itasm.dto.auth.AuthResponse;
import com.itasm.dto.auth.LoginRequest;
import com.itasm.entity.Employee;
import com.itasm.entity.UserAccount;
import com.itasm.repository.EmployeeRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.security.CurrentUser;
import com.itasm.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import com.itasm.entity.SecurityEvent;
import com.itasm.repository.SecurityEventRepository;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final SecurityEventRepository securityEventRepository;

    public AuthResponse login(LoginRequest request, String ipAddress) {
        UserAccount user = userAccountRepository.findByUsername(request.username()).orElse(null);

        if (user != null) {
            if (user.getAccountLockedUntil() != null && Instant.now().isBefore(user.getAccountLockedUntil())) {
                logEvent("ACCOUNT_LOCKED", request.username(), ipAddress, "Account is temporarily locked due to multiple failed login attempts");
                throw new BadCredentialsException("Account is temporarily locked. Please try again later.");
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            if (user != null) {
                user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
                if (user.getFailedLoginAttempts() >= 5) {
                    user.setAccountLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
                    logEvent("ACCOUNT_LOCKED", request.username(), ipAddress, "Account locked for 15 minutes after 5 failed attempts");
                } else {
                    logEvent("LOGIN_FAILED", request.username(), ipAddress, "Invalid credentials");
                }
                userAccountRepository.save(user);
            } else {
                logEvent("LOGIN_FAILED", request.username(), ipAddress, "User not found");
            }
            throw new BadCredentialsException("Invalid username or password");
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        userAccountRepository.save(user);
        
        logEvent("LOGIN_SUCCESS", request.username(), ipAddress, "Successful login");
        
        return toAuth(user);
    }

    private void logEvent(String type, String username, String ip, String details) {
        securityEventRepository.save(SecurityEvent.builder()
                .eventType(type)
                .username(username)
                .ipAddress(ip)
                .details(details)
                .build());
    }

    public AuthResponse me() {
        return toAuth(currentUser.requireUser());
    }

    private AuthResponse toAuth(UserAccount user) {
        String token = jwtService.generateToken(user.getUsername(), Map.of("role", user.getRole().name()));
        Employee employee = employeeRepository.findByUser_Id(user.getId()).orElse(null);
        return new AuthResponse(
                token, "Bearer", user.getId(), user.getUsername(), user.getRole(),
                employee != null ? employee.getId() : null,
                employee != null ? employee.getFullName() : user.getUsername()
        );
    }
}
