package com.policyinsight.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for Q&A question request.
 */
public class QuestionRequest {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 1000, message = "Question must not exceed 1000 characters")
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
}

