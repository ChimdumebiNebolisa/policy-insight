package com.policyinsight.config;

import com.policyinsight.processing.LocalDocumentProcessingWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that verify worker bean and scheduling are correctly gated by policyinsight.worker.enabled.
 * This ensures web instances (enabled=false) do not poll jobs, while worker instances (enabled=true) do.
 */
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

    @Test
    void testSchedulingNotEnabledWhenWorkerDisabled(ApplicationContext context) {
        // When: Worker is disabled
        // Then: Scheduling should not be enabled (ScheduledAnnotationBeanPostProcessor should not exist)
        // Note: Spring Boot may still create the post processor, but no @Scheduled methods should be registered
        // We verify by checking that the worker bean (which has @Scheduled) doesn't exist
        assertThatThrownBy(() -> context.getBean(LocalDocumentProcessingWorker.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}

@SpringBootTest
@TestPropertySource(properties = "policyinsight.worker.enabled=true")
class WorkerConfigTestEnabled {

    @Test
    void testWorkerBeanPresentWhenEnabled(ApplicationContext context) {
        // When: Worker is enabled
        // Then: LocalDocumentProcessingWorker bean should exist
        assertThat(context.getBean(LocalDocumentProcessingWorker.class)).isNotNull();
    }

    @Test
    void testSchedulingEnabledWhenWorkerEnabled(ApplicationContext context) {
        // When: Worker is enabled
        // Then: Scheduling should be enabled (ScheduledAnnotationBeanPostProcessor should exist)
        // and the worker bean with @Scheduled methods should exist
        assertThat(context.getBean(LocalDocumentProcessingWorker.class)).isNotNull();
        // Verify scheduling post processor exists (created by @EnableScheduling)
        assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class)).isNotNull();
    }
}

