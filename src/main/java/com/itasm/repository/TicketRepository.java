package com.itasm.repository;

import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    long countByStatus(TicketStatus status);
    long countByPriority(TicketPriority priority);
    long countByPriorityAndStatusNot(TicketPriority priority, TicketStatus status);
    long countByStatusAndResolvedAtBetween(TicketStatus status, Instant from, Instant to);
    Page<Ticket> findByReportedBy_Id(Long employeeId, Pageable pageable);

    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:q IS NULL OR :q = '' OR LOWER(t.ticketNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Ticket> search(@Param("q") String q,
                        @Param("status") TicketStatus status,
                        @Param("priority") TicketPriority priority,
                        Pageable pageable);
}
