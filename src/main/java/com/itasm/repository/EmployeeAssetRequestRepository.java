package com.itasm.repository;

import com.itasm.entity.EmployeeAssetRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeAssetRequestRepository extends JpaRepository<EmployeeAssetRequest, Long> {
    List<EmployeeAssetRequest> findByEmployee_IdOrderByCreatedAtDesc(Long employeeId);
    List<EmployeeAssetRequest> findAllByOrderByCreatedAtDesc();
}
