package com.itasm.repository;

import com.itasm.entity.SoftwareAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SoftwareAssignmentRepository extends JpaRepository<SoftwareAssignment, Long> {
    List<SoftwareAssignment> findByLicense_Id(Long licenseId);
    List<SoftwareAssignment> findByEmployee_Id(Long employeeId);
    boolean existsByLicense_IdAndEmployee_Id(Long licenseId, Long employeeId);
}
