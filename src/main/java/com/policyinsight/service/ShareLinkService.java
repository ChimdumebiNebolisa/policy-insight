package com.policyinsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.config.ShareProperties;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.Report;
import com.policyinsight.model.ShareLink;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.ReportRepository;
import com.policyinsight.repository.ShareLinkRepository;
import com.policyinsight.security.TokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareLinkService {

    private final ShareLinkRepository shareLinkRepository;
    private final ReportRepository reportRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TokenService tokenService;
    private final ShareProperties shareProperties;
    private final ObjectMapper objectMapper;

    public ShareLinkService(
            ShareLinkRepository shareLinkRepository,
            ReportRepository reportRepository,
            DocumentChunkRepository documentChunkRepository,
            TokenService tokenService,
            ShareProperties shareProperties,
            ObjectMapper objectMapper
    ) {
        this.shareLinkRepository = shareLinkRepository;
        this.reportRepository = reportRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.tokenService = tokenService;
        this.shareProperties = shareProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ShareResult createShareLink(UUID reportId, String baseUrl) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report was not found."));
        String token = tokenService.generateToken();
        shareLinkRepository.save(new ShareLink(
                report,
                tokenService.hashToken(token),
                Instant.now().plus(shareProperties.ttlDays(), ChronoUnit.DAYS)
        ));
        return new ShareResult(token, baseUrl + "/shared/" + token);
    }

    @Transactional(readOnly = true)
    public Optional<ReportView> sharedReport(String token) {
        return shareLinkRepository.findByTokenHashAndExpiresAtAfter(tokenService.hashToken(token), Instant.now())
                .map(link -> {
                    Report report = link.getReport();
                    List<DocumentChunk> chunks = documentChunkRepository.findByJobIdOrderByChunkIndex(report.getJob().getId());
                    return new ReportView(report.getId(), report.getJob().getId(), fromJson(report.getContent()), chunks);
                });
    }

    private RiskReport fromJson(String value) {
        try {
            return objectMapper.readValue(value, RiskReport.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to read report", ex);
        }
    }
}
