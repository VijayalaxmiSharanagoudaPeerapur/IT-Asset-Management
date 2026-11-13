package com.itasm.repository;

import com.itasm.domain.AssetStatus;
import com.itasm.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    boolean existsByAssetTag(String assetTag);
    boolean existsBySerialNumber(String serialNumber);
    Optional<Asset> findByAssetTag(String assetTag);
    List<Asset> findByAssignedEmployee_Id(Long employeeId);
    long countByStatus(AssetStatus status);
    List<Asset> findTop20ByRiskScoreGreaterThanOrderByRiskScoreDesc(int minScore);

    @Query("""
            SELECT a FROM Asset a
            LEFT JOIN a.assignedEmployee e
            WHERE (:status IS NULL OR a.status = :status)
              AND (:q IS NULL OR :q = '' OR LOWER(a.assetTag) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.model) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(a.brand) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR (e IS NOT NULL AND LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', :q, '%'))))
            """)
    Page<Asset> search(@Param("q") String q, @Param("status") AssetStatus status, Pageable pageable);
}
