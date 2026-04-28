package com.policyinsight.ai;

import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.List;

public interface AiAnalyzer {

    RiskReport generateReport(List<DocumentChunk> chunks);

    QaAnswer answerQuestion(List<DocumentChunk> chunks, String question);
}
