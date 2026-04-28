package com.policyinsight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.policyinsight.TestPdfFactory;
import com.policyinsight.config.UploadProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PdfProcessingTests {

    @Test
    void rejectsInvalidPdfBytes() {
        PdfValidator validator = new PdfValidator(new UploadProperties(1024));
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "not a pdf".getBytes());

        assertThatThrownBy(() -> validator.validateAndRead(file))
                .isInstanceOf(BadUploadException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void extractsPdfText() throws Exception {
        PdfTextExtractor extractor = new PdfTextExtractor();
        String text = extractor.extractText(TestPdfFactory.pdfWithText("Policy cancellation requires 30 days notice."));

        assertThat(text).contains("30 days notice");
    }

    @Test
    void createsChunks() {
        ChunkingService chunkingService = new ChunkingService();
        List<String> chunks = chunkingService.chunk("First sentence. Second sentence. Third sentence.");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.getFirst()).contains("First sentence");
    }
}
