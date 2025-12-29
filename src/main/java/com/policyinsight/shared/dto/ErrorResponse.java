package com.policyinsight.shared.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response DTO.
 */
public class ErrorResponse {

    private String error;
    private String message;
    private Instant timestamp;
    private String traceId;
    private Map<String, String> errors; // For validation errors

    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String error, String message, String traceId) {
        this.error = error;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
}

