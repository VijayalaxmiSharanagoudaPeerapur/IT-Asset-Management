package com.itasm.controller;

import com.itasm.dto.auth.AuthResponse;
import com.itasm.dto.auth.LoginRequest;
import com.itasm.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        return authService.login(request, ipAddress);
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user")
    public AuthResponse me() {
        return authService.me();
    }
}
