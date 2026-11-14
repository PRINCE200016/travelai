package com.tripmind.repository;

import com.tripmind.model.PlanSearch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlanSearchRepository extends JpaRepository<PlanSearch, Long> {
    List<PlanSearch> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable page);
    List<PlanSearch> findAllByOrderByCreatedAtDesc(Pageable page);

    @Query("SELECT p.topDestination, COUNT(p) AS c FROM PlanSearch p WHERE p.topDestination IS NOT NULL GROUP BY p.topDestination ORDER BY c DESC")
    List<Object[]> topDestinations(Pageable page);
}
