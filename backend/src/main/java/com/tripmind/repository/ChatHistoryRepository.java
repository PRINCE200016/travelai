package com.tripmind.repository;

import com.tripmind.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT c.destination, COUNT(c) as cnt FROM ChatHistory c WHERE c.destination IS NOT NULL GROUP BY c.destination ORDER BY cnt DESC")
    List<Object[]> findTopDestinations();

    long count();
}
