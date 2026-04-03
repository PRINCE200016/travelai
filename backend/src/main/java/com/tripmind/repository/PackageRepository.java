package com.tripmind.repository;

import com.tripmind.model.TravelPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackageRepository extends JpaRepository<TravelPackage, Long> {
}
