package com.policyinsight.api.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Interface for storage operations (local filesystem or GCS).
 * Implementations handle file upload and download.
 */
public interface StorageService {

    /**
     * Uploads a file to storage with the specified path structure: {jobId}/{filename}
     *
     * @param jobId      Job UUID
     * @param filename   Original filename
     * @param content    File content input stream
     * @param contentType MIME type (e.g., "application/pdf")
     * @return Storage path (format depends on implementation: local filesystem path or gs:// URI)
     * @throws IOException if upload fails
     */
    String uploadFile(UUID jobId, String filename, InputStream content, String contentType) throws IOException;

    /**
     * Downloads a file from storage by its path.
     *
     * @param storagePath Storage path returned from uploadFile (e.g., "gs://bucket/jobId/filename" or local path)
     * @return File content as byte array
     * @throws IOException if download fails
     */
    byte[] downloadFile(String storagePath) throws IOException;
}

