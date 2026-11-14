package com.tripmind.repository;

import com.tripmind.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRepository extends JpaRepository<ContactMessage, Long> {
    List<ContactMessage> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
