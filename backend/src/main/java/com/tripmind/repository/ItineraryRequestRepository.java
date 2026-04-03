package com.tripmind.repository;

import com.tripmind.model.ItineraryRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItineraryRequestRepository extends JpaRepository<ItineraryRequest, Long> {
}
