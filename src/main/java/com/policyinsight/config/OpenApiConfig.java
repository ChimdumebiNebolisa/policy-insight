package com.policyinsight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI policyInsightOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PolicyInsight API")
                        .description("Production-grade legal document analysis service with Datadog observability")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PolicyInsight Team")
                                .email("support@policyinsight.example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}

