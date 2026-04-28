package com.policyinsight.ai;

import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiAnalyzer implements AiAnalyzer {

    @Override
    public RiskReport generateReport(List<DocumentChunk> chunks) {
        UUID chunkId = chunks.isEmpty() ? null : chunks.getFirst().getId();
        List<UUID> citations = chunkId == null ? List.of() : List.of(chunkId);
        return new RiskReport(
                "Mock overview generated from " + chunks.size() + " chunk(s).",
                List.of(new CitedClaim("The document contains policy terms requiring review.", citations, false)),
                List.of(new CitedClaim("Review notice, payment, and compliance obligations.", citations, false)),
                List.of(new CitedClaim("Restrictions should be checked against cited source text.", citations, false)),
                List.of(new CitedClaim("Termination triggers should be confirmed before signing.", citations, false)),
                List.of(new CitedClaim("Medium risk until reviewed by counsel.", citations, false))
        );
    }

    @Override
    public QaAnswer answerQuestion(List<DocumentChunk> chunks, String question) {
        UUID chunkId = chunks.isEmpty() ? null : chunks.getFirst().getId();
        List<UUID> citations = chunkId == null ? List.of() : List.of(chunkId);
        return new QaAnswer("Mock answer based on saved document chunks: " + question, citations, false);
    }
}
