package com.policyinsight.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (!StringUtils.hasText(databaseUrl) || hasSpringDatasourceUrl(environment)) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();
        if (databaseUrl.startsWith("jdbc:")) {
            properties.put("spring.datasource.url", databaseUrl);
        } else {
            URI uri = URI.create(databaseUrl);
            String userInfo = uri.getUserInfo();
            if (StringUtils.hasText(userInfo)) {
                String[] parts = userInfo.split(":", 2);
                properties.put("spring.datasource.username", decode(parts[0]));
                if (parts.length > 1) {
                    properties.put("spring.datasource.password", decode(parts[1]));
                }
            }
            String query = StringUtils.hasText(uri.getQuery()) ? "?" + uri.getQuery() : "";
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            properties.put("spring.datasource.url",
                    "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + query);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", properties));
    }

    private static boolean hasSpringDatasourceUrl(ConfigurableEnvironment environment) {
        return StringUtils.hasText(environment.getProperty("SPRING_DATASOURCE_URL"))
                || StringUtils.hasText(environment.getProperty("spring.datasource.url"));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
