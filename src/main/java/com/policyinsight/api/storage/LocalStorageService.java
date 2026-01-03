package com.policyinsight.api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for storing files on the local filesystem.
 * Only loads when app.storage.mode=local (or when property is missing, as it's the default).
 */
@Service
@ConditionalOnProperty(name = "app.storage.mode", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);
    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    private final Path storageRoot;

    public LocalStorageService(
            @Value("${app.storage.local-dir:.local-storage}") String localDir) {
        this.storageRoot = Paths.get(localDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(storageRoot);
            logger.info("Local storage service initialized with directory: {}", storageRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create local storage directory: " + storageRoot, e);
        }
    }

    /**
     * Sanitizes a filename to prevent directory traversal and invalid characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "file";
        }
        // Remove path separators and invalid characters
        String sanitized = INVALID_FILENAME_CHARS.matcher(filename).replaceAll("_");
        // Prevent directory traversal
        sanitized = sanitized.replaceAll("\\.\\.", "_");
        return sanitized;
    }

    /**
     * Uploads a file to local storage with the specified path structure: jobs/{jobId}/document.pdf
     * Filename is forced to "document.pdf" for security (ignores user-provided filename).
     *
     * @param jobId      Job UUID
     * @param filename   Filename (ignored, always uses "document.pdf")
     * @param content    File content input stream
     * @param contentType MIME type (e.g., "application/pdf")
     * @return Local storage path (local://jobs/{jobId}/document.pdf)
     * @throws IOException if upload fails
     */
    @Override
    public String uploadFile(UUID jobId, String filename, InputStream content, String contentType) throws IOException {
        // Force filename to document.pdf (ignore user-provided filename for security)
        String sanitizedFilename = "document.pdf";

        // Use deterministic path: jobs/{jobId}/document.pdf
        Path jobDir = storageRoot.resolve("jobs").resolve(jobId.toString());
        Files.createDirectories(jobDir);

        Path filePath = jobDir.resolve(sanitizedFilename);

        logger.debug("Uploading file to local storage: {}", filePath);

        try {
            Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING);
            // Return deterministic path: local://jobs/{jobId}/document.pdf
            String storagePath = "local://jobs/" + jobId + "/" + sanitizedFilename;
            logger.info("Successfully uploaded file to local storage: {}", storagePath);
            return storagePath;
        } catch (Exception e) {
            logger.error("Failed to upload file to local storage: {}", filePath, e);
            throw new IOException("Failed to upload file to local storage", e);
        }
    }

    /**
     * Downloads a file from local storage by its path.
     *
     * @param storagePath Storage path returned from uploadFile (format: local://{subdir}/{jobId}/{filename})
     * @return File content as byte array
     * @throws IOException if download fails
     */
    @Override
    public byte[] downloadFile(String storagePath) throws IOException {
        if (storagePath == null || !storagePath.startsWith("local://")) {
            throw new IllegalArgumentException("Invalid local storage path: " + storagePath);
        }

        // Parse path: local://jobs/{jobId}/document.pdf
        String pathWithoutPrefix = storagePath.replace("local://", "");
        String[] parts = pathWithoutPrefix.split("/", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid local storage path format: " + storagePath);
        }

        // Support both old format (pdf/{jobId}/...) and new format (jobs/{jobId}/document.pdf)
        String subdir = parts[0];
        String jobId = parts[1];
        String filename = parts[2];

        Path filePath;
        if ("jobs".equals(subdir)) {
            // New format: jobs/{jobId}/document.pdf
            filePath = storageRoot.resolve("jobs").resolve(jobId).resolve(filename).normalize();
        } else {
            // Legacy format: pdf/{jobId}/... (for backward compatibility)
            filePath = storageRoot.resolve(subdir).resolve(jobId).resolve(filename).normalize();
        }

        // Security check: ensure the resolved path is within storage root
        if (!filePath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Path traversal detected: " + storagePath);
        }

        logger.debug("Downloading file from local storage: {}", filePath);

        try {
            if (!Files.exists(filePath)) {
                throw new IOException("File not found: " + storagePath);
            }
            byte[] content = Files.readAllBytes(filePath);
            logger.info("Successfully downloaded file from local storage: {}", storagePath);
            return content;
        } catch (Exception e) {
            logger.error("Failed to download file from local storage: {}", storagePath, e);
            throw new IOException("Failed to download file from local storage", e);
        }
    }
}

