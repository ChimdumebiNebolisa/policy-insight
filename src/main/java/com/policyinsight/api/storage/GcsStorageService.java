package com.policyinsight.api.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service for storing files in Google Cloud Storage.
 * Supports both real GCS and local emulator (via GCS_EMULATOR_HOST).
 */
@Service
public class GcsStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GcsStorageService.class);

    private final Storage storage;
    private final String bucketName;

    public GcsStorageService(
            @Value("${gcs.bucket-name:policyinsight-bucket}") String bucketName) {
        this.bucketName = bucketName;

        // StorageOptions will automatically use STORAGE_EMULATOR_HOST env var if set
        // For real GCS, use Application Default Credentials (gcloud auth application-default login)
        // or service account JSON via GOOGLE_APPLICATION_CREDENTIALS env var
        this.storage = StorageOptions.getDefaultInstance().getService();

        String emulatorHost = System.getenv("STORAGE_EMULATOR_HOST");
        if (emulatorHost != null && !emulatorHost.isEmpty()) {
            logger.info("Using GCS emulator at: {}", emulatorHost);
        } else {
            logger.info("Using real GCS (Application Default Credentials or service account)");
        }
        logger.info("GCS Storage service initialized with bucket: {}", bucketName);
    }

    /**
     * Uploads a file to GCS with the specified path structure: {jobId}/{filename}
     *
     * @param jobId      Job UUID
     * @param filename   Original filename
     * @param content    File content input stream
     * @param contentType MIME type (e.g., "application/pdf")
     * @return GCS path (gs://bucket-name/jobId/filename)
     * @throws IOException if upload fails
     */
    public String uploadFile(java.util.UUID jobId, String filename, InputStream content, String contentType) throws IOException {
        String objectName = jobId + "/" + filename;
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        logger.debug("Uploading file to GCS: gs://{}/{}", bucketName, objectName);

        try {
            storage.createFrom(blobInfo, content);
            String gcsPath = "gs://" + bucketName + "/" + objectName;
            logger.info("Successfully uploaded file to GCS: {}", gcsPath);
            return gcsPath;
        } catch (Exception e) {
            logger.error("Failed to upload file to GCS: gs://{}/{}", bucketName, objectName, e);
            throw new IOException("Failed to upload file to GCS", e);
        }
    }
}

