package com.policyinsight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Configuration
public class DebugConfig implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    private final DataSource dataSource;

    public DebugConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // #region agent log
        try {
            String dbHost = System.getenv("DB_HOST");
            String dbPort = System.getenv("DB_PORT");
            String dbName = System.getenv("DB_NAME");
            String springProfile = System.getenv("SPRING_PROFILES_ACTIVE");

            String actualDbUrl = null;
            String actualDbProduct = null;
            boolean connectionSuccess = false;
            String connectionError = null;

            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                actualDbUrl = meta.getURL();
                actualDbProduct = meta.getDatabaseProductName();
                connectionSuccess = true;
            } catch (Exception e) {
                connectionSuccess = false;
                connectionError = e.getMessage();
            }

            // Build JSON manually
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
            json.append("\"location\":\"DebugConfig.java:55\",");
            json.append("\"message\":\"Application startup - datasource config\",");
            json.append("\"data\":{");
            json.append("\"datasourceUrl\":\"").append(escapeJson(datasourceUrl)).append("\",");
            json.append("\"datasourceUsername\":\"").append(escapeJson(datasourceUsername)).append("\",");
            json.append("\"envDbHost\":\"").append(escapeJson(dbHost)).append("\",");
            json.append("\"envDbPort\":\"").append(escapeJson(dbPort)).append("\",");
            json.append("\"envDbName\":\"").append(escapeJson(dbName)).append("\",");
            json.append("\"springProfilesActive\":\"").append(escapeJson(springProfile)).append("\",");
            json.append("\"actualDatabaseUrl\":\"").append(escapeJson(actualDbUrl)).append("\",");
            json.append("\"actualDatabaseProduct\":\"").append(escapeJson(actualDbProduct)).append("\",");
            json.append("\"connectionSuccess\":").append(connectionSuccess);
            if (connectionError != null) {
                json.append(",\"connectionError\":\"").append(escapeJson(connectionError)).append("\"");
            }
            json.append("},");
            json.append("\"sessionId\":\"debug-session\",");
            json.append("\"runId\":\"run1\",");
            json.append("\"hypothesisId\":\"A\"");
            json.append("}");

            try (PrintWriter pw = new PrintWriter(new FileWriter("c:\\Users\\Chimdumebi\\policy-insight\\.cursor\\debug.log", true))) {
                pw.println(json.toString());
            }
        } catch (Exception e) {
            // Ignore logging errors
        }
        // #endregion agent log
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

