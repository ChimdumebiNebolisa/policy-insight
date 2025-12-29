package com.policyinsight.api.messaging;

import java.util.UUID;

/**
 * Interface for publishing job queued events.
 * Implementations may publish to Pub/Sub or be no-op for local development.
 */
public interface JobPublisher {
    /**
     * Publishes a job queued event.
     *
     * @param jobId Job UUID
     * @param gcsPath GCS path to the uploaded PDF
     */
    void publishJobQueued(UUID jobId, String gcsPath);
}

