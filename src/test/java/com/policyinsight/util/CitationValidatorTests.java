package com.policyinsight.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.PolicyJob;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CitationValidatorTests {

    private final CitationValidator validator = new CitationValidator();

    @Test
    void keepsValidChunkIds() {
        DocumentChunk chunk = chunkWithId(UUID.randomUUID());
        RiskReport result = validator.validateReport(reportWithIds(List.of(chunk.getId())), List.of(chunk));

        assertThat(result.summaryBullets().getFirst().chunkIds()).containsExactly(chunk.getId());
        assertThat(result.summaryBullets().getFirst().unsupported()).isFalse();
    }

    @Test
    void flagsInvalidChunkIds() {
        DocumentChunk chunk = chunkWithId(UUID.randomUUID());
        RiskReport result = validator.validateReport(reportWithIds(List.of(UUID.randomUUID())), List.of(chunk));

        assertThat(result.summaryBullets().getFirst().chunkIds()).isEmpty();
        assertThat(result.summaryBullets().getFirst().unsupported()).isTrue();
    }

    @Test
    void keepsValidIdsFromMixedCitations() {
        DocumentChunk chunk = chunkWithId(UUID.randomUUID());
        RiskReport result = validator.validateReport(
                reportWithIds(List.of(chunk.getId(), UUID.randomUUID())),
                List.of(chunk)
        );

        assertThat(result.summaryBullets().getFirst().chunkIds()).containsExactly(chunk.getId());
        assertThat(result.summaryBullets().getFirst().unsupported()).isFalse();
    }

    private static RiskReport reportWithIds(List<UUID> ids) {
        CitedClaim claim = new CitedClaim("Claim", ids, false);
        return new RiskReport("Overview", List.of(claim), List.of(claim), List.of(claim), List.of(claim), List.of(claim));
    }

    private static DocumentChunk chunkWithId(UUID id) {
        PolicyJob job = new PolicyJob("hash", Instant.now().plus(1, ChronoUnit.HOURS));
        DocumentChunk chunk = new DocumentChunk(job, 0, "Chunk text");
        ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }
}
