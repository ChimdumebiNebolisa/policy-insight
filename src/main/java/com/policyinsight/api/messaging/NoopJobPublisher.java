package com.policyinsight.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * No-op implementation of JobPublisher for local development when Pub/Sub is disabled.
 * This bean is only registered when pubsub.enabled=false or when the property is missing.
 */
@Service
@ConditionalOnProperty(prefix = "pubsub", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopJobPublisher implements JobPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NoopJobPublisher.class);

    @Override
    public void publishJobQueued(UUID jobId, String gcsPath) {
        logger.debug("No-op: Job queued event suppressed (Pub/Sub disabled). Job ID: {}, GCS Path: {}", jobId, gcsPath);
    }
}

