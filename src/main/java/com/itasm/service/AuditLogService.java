package com.itasm.service;

import com.itasm.dto.audit.AuditLogDTO;
import com.itasm.entity.AuditLog;
import com.itasm.entity.UserAccount;
import com.itasm.repository.AuditLogRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUser currentUser;
    private final Mapper mapper;

    public void logAction(String entityType, Long entityId, String action, String oldValue, String newValue) {
        UserAccount user = null;
        try {
            user = currentUser.requireUser();
        } catch (Exception e) {
            // Ignore if no current user (e.g., system actions)
        }

        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(user)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(mapper::toAuditLog);
    }
}
