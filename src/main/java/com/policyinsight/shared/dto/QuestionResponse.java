package com.policyinsight.shared.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO for Q&A question response.
 */
public class QuestionResponse {

    private UUID jobId;
    private String question;
    private String answer;
    private String confidence; // CONFIDENT, ABSTAINED
    private List<Citation> citations;

    public QuestionResponse() {
    }

    public QuestionResponse(UUID jobId, String question, String answer, String confidence) {
        this.jobId = jobId;
        this.question = question;
        this.answer = answer;
        this.confidence = confidence;
    }

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }

    /**
     * Nested DTO for citation information.
     */
    public static class Citation {
        private Long chunkId;
        private Integer pageNumber;
        private String textSpan;

        public Citation() {
        }

        public Citation(Long chunkId, Integer pageNumber, String textSpan) {
            this.chunkId = chunkId;
            this.pageNumber = pageNumber;
            this.textSpan = textSpan;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public void setChunkId(Long chunkId) {
            this.chunkId = chunkId;
        }

        public Integer getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(Integer pageNumber) {
            this.pageNumber = pageNumber;
        }

        public String getTextSpan() {
            return textSpan;
        }

        public void setTextSpan(String textSpan) {
            this.textSpan = textSpan;
        }
    }
}

