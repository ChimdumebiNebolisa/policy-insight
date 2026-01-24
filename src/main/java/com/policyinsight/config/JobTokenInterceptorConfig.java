package com.policyinsight.config;

import com.policyinsight.security.JobTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class JobTokenInterceptorConfig implements WebMvcConfigurer {

    private final JobTokenInterceptor jobTokenInterceptor;

    public JobTokenInterceptorConfig(JobTokenInterceptor jobTokenInterceptor) {
        this.jobTokenInterceptor = jobTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jobTokenInterceptor)
                .addPathPatterns(
                        "/api/documents/*/status",
                        "/api/documents/*/report-json",
                        "/api/documents/*/export/pdf",
                        "/api/documents/*/share",
                        "/api/documents/**/share/**",
                        "/documents/*/report",
                        "/api/questions/**"
                )
                .excludePathPatterns(
                        "/",
                        "/api/documents/upload",
                        "/health",
                        "/actuator/health",
                        "/actuator/readiness",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/internal/pubsub"
                );
    }
}
