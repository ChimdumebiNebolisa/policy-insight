package com.policyinsight.shared.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO for document upload request.
 */
public class UploadDocumentRequest {

    @NotNull(message = "File is required")
    private MultipartFile file;

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }
}

