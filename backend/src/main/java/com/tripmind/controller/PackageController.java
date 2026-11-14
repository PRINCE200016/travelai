package com.tripmind.controller;

import com.tripmind.model.TravelPackage;
import com.tripmind.repository.PackageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public, read-only. Create/update/delete is under /api/admin/packages. */
@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private final PackageRepository repository;

    public PackageController(PackageRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<TravelPackage>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TravelPackage> getById(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
