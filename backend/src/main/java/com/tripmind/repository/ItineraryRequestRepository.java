package com.tripmind.repository;

import com.tripmind.model.ItineraryRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItineraryRequestRepository extends JpaRepository<ItineraryRequest, Long> {
    List<ItineraryRequest> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
