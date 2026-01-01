package com.policyinsight.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * DTO for Q&A question request.
 */
public class QuestionRequest {

    private UUID documentId;

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String question;

    public QuestionRequest() {
    }

    public QuestionRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }
}

