package com.policyinsight.ai;

import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${app.ai.provider:mock}' == 'gemini' && '${app.gemini.api-key:}' == ''")
public class MissingGeminiAnalyzer implements AiAnalyzer {

    @Override
    public RiskReport generateReport(List<DocumentChunk> chunks) {
        throw new AiAnalyzerException("GEMINI_API_KEY is required when APP_AI_PROVIDER=gemini.");
    }

    @Override
    public QaAnswer answerQuestion(List<DocumentChunk> chunks, String question) {
        throw new AiAnalyzerException("GEMINI_API_KEY is required when APP_AI_PROVIDER=gemini.");
    }
}
