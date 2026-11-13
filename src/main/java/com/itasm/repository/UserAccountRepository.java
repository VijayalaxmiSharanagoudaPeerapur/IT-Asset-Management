package com.itasm.repository;

import com.itasm.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    java.util.List<UserAccount> findByRole(com.itasm.domain.Role role);
}
