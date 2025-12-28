package com.policyinsight.config;

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

    public ReadinessHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
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

