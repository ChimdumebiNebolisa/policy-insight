package com.policyinsight.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final ReadinessHealthIndicator readinessHealthIndicator;

    public HealthController(ReadinessHealthIndicator readinessHealthIndicator) {
        this.readinessHealthIndicator = readinessHealthIndicator;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        // Get database health from readiness indicator
        Health dbHealth = readinessHealthIndicator.health();

        Map<String, Object> response = new HashMap<>();
        response.put("status", dbHealth.getStatus().getCode());
        response.put("timestamp", Instant.now().toString());

        // For Milestone 1, we only have database check
        // In later milestones, add GCS, VertexAI, Pub/Sub checks
        Map<String, Object> checks = new HashMap<>();
        if (dbHealth.getDetails().containsKey("database")) {
            checks.put("db", dbHealth.getDetails().get("database"));
        } else {
            checks.put("db", "UP");
        }
        response.put("checks", checks);

        return ResponseEntity.ok(response);
    }
}

