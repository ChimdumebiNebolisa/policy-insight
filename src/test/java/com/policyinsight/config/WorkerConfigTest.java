package com.policyinsight.config;

import com.policyinsight.processing.LocalDocumentProcessingWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "policyinsight.worker.enabled=false")
class WorkerConfigTestDisabled {

    @Test
    void testWorkerBeanNotPresentWhenDisabled(ApplicationContext context) {
        // When: Worker is disabled
        // Then: LocalDocumentProcessingWorker bean should not exist
        assertThatThrownBy(() -> context.getBean(LocalDocumentProcessingWorker.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}

@SpringBootTest
@TestPropertySource(properties = {
        "policyinsight.worker.enabled=true",
        "app.processing.mode=local"
})
class WorkerConfigTestEnabled {

    @Test
    void testWorkerBeanPresentWhenEnabled(ApplicationContext context) {
        // When: Worker is enabled and processing mode is local
        // Then: LocalDocumentProcessingWorker bean should exist
        assertThat(context.getBean(LocalDocumentProcessingWorker.class)).isNotNull();
    }
}

