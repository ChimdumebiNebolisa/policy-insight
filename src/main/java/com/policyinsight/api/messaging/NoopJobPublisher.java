package com.policyinsight.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * No-op implementation of JobPublisher for local development when messaging mode is local.
 * This bean is only registered when app.messaging.mode=local or when the property is missing.
 */
@Service
@ConditionalOnProperty(name = "app.messaging.mode", havingValue = "local", matchIfMissing = true)
public class NoopJobPublisher implements JobPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NoopJobPublisher.class);

    @Override
    public void publishJobQueued(UUID jobId, String gcsPath, String requestId) {
        logger.debug("No-op: Job queued event suppressed (Pub/Sub disabled). Job ID: {}, GCS Path: {}, Request ID: {}", jobId, gcsPath, requestId);
    }
}

