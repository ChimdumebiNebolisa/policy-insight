package com.policyinsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.AiAnalyzer;
import com.policyinsight.ai.dto.QaAnswer;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.QaInteraction;
import com.policyinsight.model.Report;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.QaInteractionRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.util.CitationValidator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QaService {

    private final AiAnalyzer aiAnalyzer;
    private final CitationValidator citationValidator;
    private final DocumentChunkRepository documentChunkRepository;
    private final ReportRepository reportRepository;
    private final QaInteractionRepository qaInteractionRepository;
    private final ObjectMapper objectMapper;

    public QaService(
            AiAnalyzer aiAnalyzer,
            CitationValidator citationValidator,
            DocumentChunkRepository documentChunkRepository,
            ReportRepository reportRepository,
            QaInteractionRepository qaInteractionRepository,
            ObjectMapper objectMapper
    ) {
        this.aiAnalyzer = aiAnalyzer;
        this.citationValidator = citationValidator;
        this.documentChunkRepository = documentChunkRepository;
        this.reportRepository = reportRepository;
        this.qaInteractionRepository = qaInteractionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QaAnswer answer(UUID reportId, String question) {
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new BadUploadException("Question must be between 1 and 1000 characters.");
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report was not found."));
        List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(report.getJob().getId());
        QaAnswer answer = citationValidator.validateAnswer(aiAnalyzer.answerQuestion(chunks, question.trim()), chunks);
        qaInteractionRepository.save(new QaInteraction(report, question.trim(), toJson(answer)));
        return answer;
    }

    private String toJson(QaAnswer answer) {
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize answer", ex);
        }
    }
}
