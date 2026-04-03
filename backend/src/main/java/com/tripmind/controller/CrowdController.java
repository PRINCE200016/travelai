package com.tripmind.controller;

import com.tripmind.service.CrowdService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CrowdController {

    private final CrowdService crowdService;

    public CrowdController(CrowdService crowdService) {
        this.crowdService = crowdService;
    }

    @GetMapping("/crowd")
    public ResponseEntity<Map<String, String>> getCrowd(@RequestParam String destination) {
        Map<String, String> crowd = crowdService.getCrowdLevel(destination);
        return ResponseEntity.ok(crowd);
    }
}
