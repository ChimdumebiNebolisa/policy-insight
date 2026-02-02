package com.policyinsight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Component
public class ReadinessHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    @Value("${app.demo-sleep:false}")
    private boolean demoSleep;

    public ReadinessHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        if (demoSleep) {
            Map<String, Object> details = new HashMap<>();
            details.put("mode", "sleep");
            details.put("database", "skipped");
            return Health.up().withDetails(details).build();
        }

        Map<String, Object> details = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                details.put("database", "UP");
                details.put("connection", "Valid");
                return Health.up()
                        .withDetails(details)
                        .build();
            } else {
                details.put("database", "DOWN");
                details.put("connection", "Invalid");
                return Health.down()
                        .withDetails(details)
                        .build();
            }
        } catch (SQLException e) {
            details.put("database", "DOWN");
            details.put("error", e.getMessage());
            return Health.down()
                    .withDetails(details)
                    .build();
        }
    }
}

