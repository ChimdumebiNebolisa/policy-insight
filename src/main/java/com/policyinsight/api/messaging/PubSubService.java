package com.policyinsight.api.messaging;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for publishing messages to Google Cloud Pub/Sub.
 * Supports both real Pub/Sub and local emulator (via PUBSUB_EMULATOR_HOST).
 * Only loads when pubsub.enabled=true.
 */
@Service
@ConditionalOnProperty(prefix = "pubsub", name = "enabled", havingValue = "true")
public class PubSubService implements JobPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PubSubService.class);

    private final String projectId;
    private final String topicName;
    private Publisher publisher;

    public PubSubService(
            @Value("${pubsub.project-id:#{T(java.lang.System).getenv('GOOGLE_CLOUD_PROJECT')}}") String projectId,
            @Value("${pubsub.topic-name:document-analysis-topic}") String topicName) {
        this.projectId = projectId != null && !projectId.isEmpty() ? projectId : "local-project";
        this.topicName = topicName;
    }

    @PostConstruct
    public void initialize() {
        try {
            TopicName topic = TopicName.of(projectId, topicName);

            // Publisher will automatically use PUBSUB_EMULATOR_HOST env var if set
            this.publisher = Publisher.newBuilder(topic).build();

            String emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST");
            if (emulatorHost != null && !emulatorHost.isEmpty()) {
                logger.info("Using Pub/Sub emulator at: {}", emulatorHost);
            } else {
                logger.info("Using real Pub/Sub (Application Default Credentials or service account)");
            }
            logger.info("Pub/Sub service initialized for topic: projects/{}/topics/{}", projectId, topicName);
        } catch (Exception e) {
            logger.error("Failed to initialize Pub/Sub publisher", e);
            throw new RuntimeException("Failed to initialize Pub/Sub publisher", e);
        }
    }

    /**
     * Publishes a job analysis message to Pub/Sub.
     *
     * @param jobId Job UUID
     * @param gcsPath GCS path to the uploaded PDF
     */
    @Override
    public void publishJobQueued(UUID jobId, String gcsPath) {
        // Create message payload as JSON string
        String payload = String.format("{\"job_id\":\"%s\",\"gcs_path\":\"%s\"}", jobId.toString(), gcsPath);

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payload))
                .putAttributes("job_id", jobId.toString())
                .putAttributes("action", "ANALYZE")
                .build();

        logger.debug("Publishing message to Pub/Sub topic: {} for job: {}", topicName, jobId);

        try {
            ApiFuture<String> future = publisher.publish(message);
            String messageId = future.get(10, TimeUnit.SECONDS);
            logger.info("Successfully published message to Pub/Sub. Message ID: {}, Job ID: {}", messageId, jobId);
        } catch (Exception e) {
            logger.error("Failed to publish message to Pub/Sub for job: {}", jobId, e);
            throw new RuntimeException("Failed to publish message to Pub/Sub", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (publisher != null) {
            try {
                publisher.shutdown();
                publisher.awaitTermination(5, TimeUnit.SECONDS);
                logger.info("Pub/Sub publisher shut down successfully");
            } catch (Exception e) {
                logger.error("Error shutting down Pub/Sub publisher", e);
            }
        }
    }
}

