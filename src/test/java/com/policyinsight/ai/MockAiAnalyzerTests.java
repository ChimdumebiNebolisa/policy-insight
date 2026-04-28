package com.policyinsight.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.PolicyJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MockAiAnalyzerTests {

    @Test
    void generatesReportWithChunkIds() {
        PolicyJob job = new PolicyJob("hash", Instant.now().plus(1, ChronoUnit.HOURS));
        DocumentChunk chunk = new DocumentChunk(job, 0, "The policy requires written notice.");
        UUID chunkId = UUID.randomUUID();
        ReflectionTestUtils.setField(chunk, "id", chunkId);
        MockAiAnalyzer analyzer = new MockAiAnalyzer();

        assertThat(analyzer.generateReport(java.util.List.of(chunk)).summaryBullets().getFirst().chunkIds())
                .containsExactly(chunkId);
    }
}
