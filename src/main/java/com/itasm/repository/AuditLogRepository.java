package com.itasm.repository;

import com.itasm.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @EntityGraph(attributePaths = {"performedBy"})
    Page<AuditLog> findByEntityTypeOrderByTimestampDesc(String entityType, Pageable pageable);

    @EntityGraph(attributePaths = {"performedBy"})
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
