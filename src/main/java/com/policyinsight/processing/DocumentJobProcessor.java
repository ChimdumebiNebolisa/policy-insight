package com.policyinsight.processing;

import java.util.UUID;

/**
 * Interface for processing document jobs.
 * Implemented by both LocalDocumentProcessingWorker (local polling) and
 * cloud-based processing services (Pub/Sub push handlers).
 */
public interface DocumentJobProcessor {
    /**
     * Process a document job by its UUID.
     *
     * @param jobId the UUID of the job to process
     * @throws Exception if processing fails
     */
    void processDocument(UUID jobId) throws Exception;
}

