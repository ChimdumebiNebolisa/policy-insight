package com.policyinsight.service;

import com.policyinsight.config.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfValidator {

    private static final byte[] PDF_MAGIC = new byte[] {'%', 'P', 'D', 'F', '-'};
    private final UploadProperties uploadProperties;

    public PdfValidator(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadUploadException("Upload a non-empty PDF file.");
        }
        if (file.getSize() > uploadProperties.maxBytes()) {
            throw new BadUploadException("PDF is larger than the allowed upload size.");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!hasPdfMagic(bytes)) {
                throw new BadUploadException("Uploaded file must be a PDF.");
            }
            return bytes;
        } catch (BadUploadException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadUploadException("Unable to read uploaded PDF.");
        }
    }

    private static boolean hasPdfMagic(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
