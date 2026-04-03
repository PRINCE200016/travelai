package com.tripmind.repository;

import com.tripmind.model.ChatMessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageLogRepository extends JpaRepository<ChatMessageLog, Long> {
    List<ChatMessageLog> findAllByOrderByCreatedAtDesc();
    List<ChatMessageLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
