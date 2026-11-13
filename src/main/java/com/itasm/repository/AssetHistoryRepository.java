package com.itasm.repository;

import com.itasm.entity.AssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Long> {
    List<AssetHistory> findByAsset_IdOrderByCreatedAtDesc(Long assetId);
}
