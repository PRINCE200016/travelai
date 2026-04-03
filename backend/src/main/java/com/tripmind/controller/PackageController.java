package com.tripmind.controller;

import com.tripmind.model.TravelPackage;
import com.tripmind.repository.PackageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/packages")
@CrossOrigin(origins = "*")
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
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TravelPackage> create(@RequestBody TravelPackage pkg) {
        return ResponseEntity.ok(repository.save(pkg));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TravelPackage> update(@PathVariable Long id, @RequestBody TravelPackage pkg) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setTitle(pkg.getTitle());
                    existing.setPrice(pkg.getPrice());
                    existing.setImageUrl(pkg.getImageUrl());
                    existing.setDescription(pkg.getDescription());
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
