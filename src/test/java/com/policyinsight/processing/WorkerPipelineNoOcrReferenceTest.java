package com.policyinsight.processing;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerPipelineNoOcrReferenceTest {

    @Test
    void workerPipelineDoesNotReferenceOcrVendor() {
        assertNoOcrVendorFields(DocumentProcessingWorker.class);
        assertNoOcrVendorFields(LocalDocumentProcessingWorker.class);
    }

    private void assertNoOcrVendorFields(Class<?> clazz) {
        String forbidden = String.join("", "document", "ai");
        for (Field field : clazz.getDeclaredFields()) {
            assertThat(field.getName().toLowerCase()).doesNotContain(forbidden);
            assertThat(field.getType().getSimpleName().toLowerCase()).doesNotContain(forbidden);
        }
    }
}
