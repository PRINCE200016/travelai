package com.tripmind.controller;

import com.tripmind.model.PaymentRequest;
import com.tripmind.repository.PaymentRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentRequestRepository paymentRepo;

    public PaymentController(PaymentRequestRepository paymentRepo) {
        this.paymentRepo = paymentRepo;
    }

    @PostMapping("/payment")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequest payment, HttpServletRequest httpRequest) {
        Object attrUserId = httpRequest.getAttribute("userId");
        if (payment.getUserId() == null && attrUserId instanceof Long) {
            payment.setUserId((Long) attrUserId);
        }
        if (payment.getRequestType() == null || payment.getRequestType().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "requestType is required"));
        }
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "amount must be positive"));
        }

        PaymentRequest saved = paymentRepo.save(payment);
        log.info("Payment request created id={}, userId={}, status={}, amount={}",
                saved.getId(), saved.getUserId(), saved.getPaymentStatus(), saved.getAmount());
        return ResponseEntity.ok(saved);
    }

    // Admin view
    @GetMapping("/admin/payments")
    public ResponseEntity<List<PaymentRequest>> getPaymentsForAdmin() {
        return ResponseEntity.ok(paymentRepo.findAllByOrderByCreatedAtDesc());
    }
}
