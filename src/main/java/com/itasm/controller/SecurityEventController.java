package com.itasm.controller;

import com.itasm.dto.auth.SecurityEventResponse;
import com.itasm.entity.SecurityEvent;
import com.itasm.repository.SecurityEventRepository;
import com.itasm.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/security-events")
@RequiredArgsConstructor
@Tag(name = "Security Monitoring")
public class SecurityEventController {

    private final SecurityEventRepository securityEventRepository;
    private final CurrentUser currentUser;

    @GetMapping
    @Operation(summary = "View security events (Admin only)")
    public List<SecurityEventResponse> getSecurityEvents() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only administrators can view security events");
        }
        
        return securityEventRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(event -> new SecurityEventResponse(
                        event.getId(),
                        event.getTimestamp(),
                        event.getEventType(),
                        event.getUsername(),
                        event.getIpAddress(),
                        event.getDetails()
                ))
                .collect(Collectors.toList());
    }
}
