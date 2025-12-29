package com.policyinsight.api;

import com.policyinsight.shared.dto.ShareLinkResponse;
import com.policyinsight.shared.model.ShareLink;
import com.policyinsight.shared.repository.ShareLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing shareable links with 7-day TTL.
 */
@Service
public class ShareLinkService {

    private static final Logger logger = LoggerFactory.getLogger(ShareLinkService.class);

    private final ShareLinkRepository shareLinkRepository;

    public ShareLinkService(ShareLinkRepository shareLinkRepository) {
        this.shareLinkRepository = shareLinkRepository;
    }

    /**
     * Generate a new shareable link for a document.
     *
     * @param jobUuid the job UUID
     * @param baseUrl the base URL for constructing the share URL
     * @return ShareLinkResponse with share URL and expiration
     */
    @Transactional
    public ShareLinkResponse generateShareLink(UUID jobUuid, String baseUrl) {
        logger.info("Generating share link for job: {}", jobUuid);

        // Check if a valid (non-expired) link already exists
        ShareLink existingLink = shareLinkRepository.findByJobUuid(jobUuid)
                .stream()
                .filter(link -> !link.isExpired())
                .findFirst()
                .orElse(null);

        if (existingLink != null) {
            logger.info("Reusing existing share link for job: {}", jobUuid);
            return buildResponse(existingLink, baseUrl);
        }

        // Create new share link
        ShareLink shareLink = new ShareLink(jobUuid);
        shareLink = shareLinkRepository.save(shareLink);
        logger.info("Created new share link: token={}, expiresAt={}",
                shareLink.getShareToken(), shareLink.getExpiresAt());

        return buildResponse(shareLink, baseUrl);
    }

    /**
     * Validate and retrieve a share link by token.
     * Increments access count if valid.
     *
     * @param token the share token
     * @return ShareLink if valid and not expired, null otherwise
     */
    @Transactional
    public ShareLink validateAndAccessShareLink(UUID token) {
        logger.info("Validating share link token: {}", token);

        ShareLink shareLink = shareLinkRepository.findByShareToken(token)
                .orElse(null);

        if (shareLink == null) {
            logger.warn("Share link not found: {}", token);
            return null;
        }

        if (shareLink.isExpired()) {
            logger.warn("Share link expired: token={}, expiresAt={}",
                    token, shareLink.getExpiresAt());
            return null;
        }

        // Increment access count
        shareLink.incrementAccessCount();
        shareLinkRepository.save(shareLink);
        logger.info("Share link accessed: token={}, accessCount={}",
                token, shareLink.getAccessCount());

        return shareLink;
    }

    private ShareLinkResponse buildResponse(ShareLink shareLink, String baseUrl) {
        String shareUrl = baseUrl + "/documents/" + shareLink.getJobUuid() +
                "/share/" + shareLink.getShareToken();

        return new ShareLinkResponse(
                shareLink.getJobUuid(),
                shareUrl,
                shareLink.getExpiresAt(),
                "Link expires in 7 days. Recipient cannot modify or ask questions."
        );
    }
}

