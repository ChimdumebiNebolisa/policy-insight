package com.policyinsight.config;

import com.policyinsight.processing.DocumentJobProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.UUID;

/**
 * Configuration that provides a stub DocumentJobProcessor bean for tests.
 * This is needed because PubSubController requires a DocumentJobProcessor,
 * but the actual implementations (LocalDocumentProcessingWorker, DocumentProcessingWorker)
 * are conditionally loaded based on configuration.
 *
 * This bean is only created when:
 * 1. The "test" profile is active, AND
 * 2. No other DocumentJobProcessor bean exists
 *
 * Tests that need actual processing should enable the worker
 * via policyinsight.worker.enabled=true in their test configuration.
 */
@Configuration
@Profile("test")
public class TestDocumentJobProcessorConfig {

    @Bean
    @ConditionalOnMissingBean(DocumentJobProcessor.class)
    public DocumentJobProcessor testDocumentJobProcessor() {
        return new DocumentJobProcessor() {
            @Override
            public void processDocument(UUID jobId) throws Exception {
                // No-op implementation for tests
                // Tests that need actual processing should enable the worker
                // via policyinsight.worker.enabled=true in their test configuration
            }
        };
    }
}

