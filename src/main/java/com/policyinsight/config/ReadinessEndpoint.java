package com.policyinsight.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class ReadinessEndpoint {

    private final ReadinessHealthIndicator readinessHealthIndicator;

    public ReadinessEndpoint(ReadinessHealthIndicator readinessHealthIndicator) {
        this.readinessHealthIndicator = readinessHealthIndicator;
    }

    @GetMapping("/readiness")
    public ResponseEntity<Map<String, Object>> readiness() {
        Health health = readinessHealthIndicator.health();
        Map<String, Object> response = new HashMap<>();
        response.put("status", health.getStatus().getCode());
        response.put("details", health.getDetails());
        return ResponseEntity.ok(response);
    }
}

