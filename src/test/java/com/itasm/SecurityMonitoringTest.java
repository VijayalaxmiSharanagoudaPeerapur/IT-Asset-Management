package com.itasm;

import com.itasm.domain.Role;
import com.itasm.dto.auth.AuthResponse;
import com.itasm.dto.auth.LoginRequest;
import com.itasm.entity.SecurityEvent;
import com.itasm.entity.UserAccount;
import com.itasm.repository.EmployeeRepository;
import com.itasm.repository.SecurityEventRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.security.CurrentUser;
import com.itasm.security.JwtService;
import com.itasm.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityMonitoringTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private SecurityEventRepository securityEventRepository;

    @InjectMocks
    private AuthService authService;

    private UserAccount testUser;

    @BeforeEach
    void setUp() {
        testUser = UserAccount.builder()
                .id(5L)
                .username("john.doe")
                .role(Role.EMPLOYEE)
                .failedLoginAttempts(0)
                .accountLockedUntil(null)
                .build();
    }

    @Test
    void login_Success_RecordsSecurityEventAndResetsCounters() {
        when(userAccountRepository.findByUsername("john.doe")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(), any())).thenReturn("jwt-token-123");
        when(employeeRepository.findByUser_Id(5L)).thenReturn(Optional.empty());

        AuthResponse resp = authService.login(new LoginRequest("john.doe", "password123"), "192.168.1.100");

        assertNotNull(resp);
        assertEquals("jwt-token-123", resp.token());
        assertEquals(0, testUser.getFailedLoginAttempts());
        assertNull(testUser.getAccountLockedUntil());

        verify(userAccountRepository, atLeastOnce()).save(testUser);
        verify(securityEventRepository).save(argThat(event ->
                "LOGIN_SUCCESS".equals(event.getEventType()) &&
                "john.doe".equals(event.getUsername()) &&
                "192.168.1.100".equals(event.getIpAddress())
        ));
    }

    @Test
    void login_FailedAttempt_IncrementsCountAndRecordsFailedEvent() {
        when(userAccountRepository.findByUsername("john.doe")).thenReturn(Optional.of(testUser));
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("john.doe", "wrongpassword"), "10.0.0.5")
        );

        assertEquals(1, testUser.getFailedLoginAttempts());
        verify(userAccountRepository).save(testUser);
        verify(securityEventRepository).save(argThat(event ->
                "LOGIN_FAILED".equals(event.getEventType()) &&
                "john.doe".equals(event.getUsername()) &&
                "10.0.0.5".equals(event.getIpAddress())
        ));
    }

    @Test
    void login_FiveConsecutiveFailures_LocksAccountFor15Minutes() {
        testUser.setFailedLoginAttempts(4);
        when(userAccountRepository.findByUsername("john.doe")).thenReturn(Optional.of(testUser));
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("john.doe", "wrongpassword"), "10.0.0.5")
        );

        assertEquals(5, testUser.getFailedLoginAttempts());
        assertNotNull(testUser.getAccountLockedUntil());
        assertTrue(testUser.getAccountLockedUntil().isAfter(Instant.now()));

        verify(securityEventRepository).save(argThat(event ->
                "ACCOUNT_LOCKED".equals(event.getEventType()) &&
                "john.doe".equals(event.getUsername())
        ));
    }

    @Test
    void login_WhenAlreadyLocked_RejectsImmediatelyAndRecordsLockedEvent() {
        testUser.setAccountLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(userAccountRepository.findByUsername("john.doe")).thenReturn(Optional.of(testUser));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("john.doe", "anypassword"), "192.168.1.50")
        );

        assertTrue(ex.getMessage().contains("Account is temporarily locked"));
        verifyNoInteractions(authenticationManager);
        verify(securityEventRepository).save(argThat(event ->
                "ACCOUNT_LOCKED".equals(event.getEventType()) &&
                "john.doe".equals(event.getUsername())
        ));
    }
}
