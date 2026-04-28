package com.policyinsight.service;

public class BadUploadException extends RuntimeException {

    public BadUploadException(String message) {
        super(message);
    }
}
