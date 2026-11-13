package com.itasm.repository;

import com.itasm.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = {"vendor"})
    List<PurchaseOrder> findAll();
}
