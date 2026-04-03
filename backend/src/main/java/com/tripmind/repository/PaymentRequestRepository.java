package com.tripmind.repository;

import com.tripmind.model.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {
    List<PaymentRequest> findAllByOrderByCreatedAtDesc();
}
