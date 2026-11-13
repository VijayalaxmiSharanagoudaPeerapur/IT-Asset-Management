package com.itasm.repository;

import com.itasm.entity.SoftwareLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SoftwareLicenseRepository extends JpaRepository<SoftwareLicense, Long> {
    List<SoftwareLicense> findAllByOrderByNameAsc();
}
