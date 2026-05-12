package com.policyinsight.service;

import com.policyinsight.config.OwnerTokenProperties;
import com.policyinsight.config.PasteProperties;
import com.policyinsight.model.DocumentChunk;
import com.policyinsight.model.JobStatus;
import com.policyinsight.model.PolicyJob;
import com.policyinsight.repository.DocumentChunkRepository;
import com.policyinsight.repository.PolicyJobRepository;
import com.policyinsight.security.TokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final PdfValidator pdfValidator;
    private final PdfTextExtractor pdfTextExtractor;
    private final ChunkingService chunkingService;
    private final PolicyJobRepository policyJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TokenService tokenService;
    private final OwnerTokenProperties ownerTokenProperties;
    private final PasteProperties pasteProperties;
    private final AsyncReportGenerationService asyncReportGenerationService;

    public DocumentService(
            PdfValidator pdfValidator,
            PdfTextExtractor pdfTextExtractor,
            ChunkingService chunkingService,
            PolicyJobRepository policyJobRepository,
            DocumentChunkRepository documentChunkRepository,
            TokenService tokenService,
            OwnerTokenProperties ownerTokenProperties,
            PasteProperties pasteProperties,
            AsyncReportGenerationService asyncReportGenerationService
    ) {
        this.pdfValidator = pdfValidator;
        this.pdfTextExtractor = pdfTextExtractor;
        this.chunkingService = chunkingService;
        this.policyJobRepository = policyJobRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.tokenService = tokenService;
        this.ownerTokenProperties = ownerTokenProperties;
        this.pasteProperties = pasteProperties;
        this.asyncReportGenerationService = asyncReportGenerationService;
    }

    public UploadResult upload(MultipartFile file) {
        byte[] pdfBytes = pdfValidator.validateAndRead(file);
        String text = pdfTextExtractor.extractText(pdfBytes);
        return ingestExtractedText(text, "No extractable text was found in the PDF.");
    }

    public UploadResult pasteText(String rawText) {
        if (rawText == null) {
            throw new BadUploadException("Paste text cannot be empty.");
        }
        String text = rawText.strip();
        if (text.isEmpty()) {
            throw new BadUploadException("Paste text cannot be empty.");
        }
        if (text.length() > pasteProperties.maxChars()) {
            throw new BadUploadException("Pasted text is too long. Shorten it and try again.");
        }
        return ingestExtractedText(text, "No extractable text could be processed from the pasted content.");
    }

    private UploadResult ingestExtractedText(String text, String emptyChunksMessage) {
        List<String> chunks = chunkingService.chunk(text);
        if (chunks.isEmpty()) {
            throw new BadUploadException(emptyChunksMessage);
        }

        String ownerToken = tokenService.generateToken();
        PolicyJob job = policyJobRepository.save(new PolicyJob(
                tokenService.hashToken(ownerToken),
                Instant.now().plus(ownerTokenProperties.ttlMinutes(), ChronoUnit.MINUTES)
        ));
        job.setStatus(JobStatus.UPLOADED);
        policyJobRepository.save(job);
        for (int i = 0; i < chunks.size(); i++) {
            documentChunkRepository.save(new DocumentChunk(job, i, chunks.get(i)));
        }
        job.setStatus(JobStatus.TEXT_EXTRACTED);
        policyJobRepository.save(job);
        asyncReportGenerationService.generate(job.getId());
        return new UploadResult(job.getId(), ownerToken, chunks.size());
    }
}
