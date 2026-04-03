package com.tripmind.repository;

import com.tripmind.model.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByToken(String token);
    
    @Transactional
    void deleteByToken(String token);
}
