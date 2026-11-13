package com.itasm.repository;

import com.itasm.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmployeeCode(String employeeCode);
    Optional<Employee> findByEmail(String email);
    Optional<Employee> findByUser_Id(Long userId);
    boolean existsByEmployeeCode(String employeeCode);
    boolean existsByEmail(String email);
    long countByActiveTrue();

    @Query("""
            SELECT e FROM Employee e
            WHERE (:active IS NULL OR e.active = :active)
              AND (:department IS NULL OR :department = '' OR LOWER(e.department) = LOWER(:department))
              AND (:q IS NULL OR :q = '' OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(e.email) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Employee> search(@Param("q") String q,
                          @Param("department") String department,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
