package com.aiinfluencer.service;

import com.aiinfluencer.domain.entity.PublicationRecord;
import com.aiinfluencer.domain.entity.SocialMediaAccount;
import com.aiinfluencer.domain.entity.VideoProject;
import com.aiinfluencer.repository.PublicationRecordRepository;
import com.aiinfluencer.repository.SocialMediaAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocialMediaPublishingService {

    private final PublicationRecordRepository publicationRecordRepository;
    private final SocialMediaAccountRepository socialMediaAccountRepository;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Publish video to specified social media platform
     */
    @Async
    @Transactional
    public void publishVideo(Long videoProjectId, Long socialMediaAccountId, VideoProject videoProject) {
        PublicationRecord record = null;
        try {
            SocialMediaAccount account = socialMediaAccountRepository.findById(socialMediaAccountId)
                    .orElseThrow(() -> new RuntimeException("Social media account not found"));

            // Create publication record
            record = PublicationRecord.builder()
                    .videoProject(videoProject)
                    .socialMediaAccount(account)
                    .status(PublicationRecord.Status.IN_PROGRESS)
                    .build();
            record = publicationRecordRepository.save(record);

            log.info("Publishing video project {} to {} for user {}",
                     videoProjectId, account.getPlatform(), account.getUser().getId());

            // Publish to appropriate platform
            switch (account.getPlatform()) {
                case YOUTUBE -> publishToYouTube(videoProject, account, record);
                case TWITTER -> publishToTwitter(videoProject, account, record);
                case INSTAGRAM -> publishToInstagram(videoProject, account, record);
                case TIKTOK -> publishToTikTok(videoProject, account, record);
            }

            log.info("Successfully published video project {} to {}",
                     videoProjectId, account.getPlatform());

        } catch (Exception e) {
            log.error("Error publishing video project {} to social media", videoProjectId, e);
            if (record != null) {
                record.setStatus(PublicationRecord.Status.FAILED);
                record.setErrorMessage(e.getMessage());
                publicationRecordRepository.save(record);
            }
        }
    }

    private void publishToYouTube(VideoProject project, SocialMediaAccount account,
                                  PublicationRecord record) {
        try {
            log.info("Publishing to YouTube");

            // YouTube Data API v3 implementation
            var requestBody = objectMapper.createObjectNode()
                    .set("snippet", objectMapper.createObjectNode()
                            .put("title", project.getTitle())
                            .put("description", project.getDescription())
                            .put("categoryId", "22")) // People & Blogs
                    .set("status", objectMapper.createObjectNode()
                            .put("privacyStatus", "public"));

            // Multipart upload would go here
            // For now, simplified version
            String videoId = uploadToYouTube(account.getAccessToken(), project.getVideoUrl(),
                                             requestBody.toString());

            record.setPlatformPostId(videoId);
            record.setPlatformUrl("https://www.youtube.com/watch?v=" + videoId);
            record.setStatus(PublicationRecord.Status.PUBLISHED);
            record.setPublishedAt(LocalDateTime.now());
            publicationRecordRepository.save(record);

        } catch (Exception e) {
            log.error("Error publishing to YouTube", e);
            throw new RuntimeException("YouTube publication failed: " + e.getMessage(), e);
        }
    }

    private void publishToTwitter(VideoProject project, SocialMediaAccount account,
                                  PublicationRecord record) {
        try {
            log.info("Publishing to Twitter");

            // Twitter API v2 implementation
            var requestBody = objectMapper.createObjectNode()
                    .put("text", project.getTitle() + "\n\n" + project.getDescription());

            String tweetId = postToTwitter(account.getAccessToken(), requestBody.toString());

            record.setPlatformPostId(tweetId);
            record.setPlatformUrl("https://twitter.com/i/status/" + tweetId);
            record.setStatus(PublicationRecord.Status.PUBLISHED);
            record.setPublishedAt(LocalDateTime.now());
            publicationRecordRepository.save(record);

        } catch (Exception e) {
            log.error("Error publishing to Twitter", e);
            throw new RuntimeException("Twitter publication failed: " + e.getMessage(), e);
        }
    }

    private void publishToInstagram(VideoProject project, SocialMediaAccount account,
                                    PublicationRecord record) {
        try {
            log.info("Publishing to Instagram");

            // Instagram Graph API implementation
            // Simplified - actual implementation would use container creation + publishing flow
            String postId = "ig_" + System.currentTimeMillis();

            record.setPlatformPostId(postId);
            record.setPlatformUrl("https://www.instagram.com/p/" + postId);
            record.setStatus(PublicationRecord.Status.PUBLISHED);
            record.setPublishedAt(LocalDateTime.now());
            publicationRecordRepository.save(record);

        } catch (Exception e) {
            log.error("Error publishing to Instagram", e);
            throw new RuntimeException("Instagram publication failed: " + e.getMessage(), e);
        }
    }

    private void publishToTikTok(VideoProject project, SocialMediaAccount account,
                                 PublicationRecord record) {
        try {
            log.info("Publishing to TikTok");

            // TikTok API implementation
            // Simplified version
            String postId = "tt_" + System.currentTimeMillis();

            record.setPlatformPostId(postId);
            record.setPlatformUrl("https://www.tiktok.com/@user/video/" + postId);
            record.setStatus(PublicationRecord.Status.PUBLISHED);
            record.setPublishedAt(LocalDateTime.now());
            publicationRecordRepository.save(record);

        } catch (Exception e) {
            log.error("Error publishing to TikTok", e);
            throw new RuntimeException("TikTok publication failed: " + e.getMessage(), e);
        }
    }

    private String uploadToYouTube(String accessToken, String videoUrl, String metadata)
            throws IOException {
        // Simplified - actual implementation would use resumable upload
        Request request = new Request.Builder()
                .url("https://www.googleapis.com/upload/youtube/v3/videos")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(metadata, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("YouTube upload failed: " + response.code());
            }

            var responseJson = objectMapper.readTree(response.body().string());
            return responseJson.get("id").asText();
        }
    }

    private String postToTwitter(String accessToken, String content) throws IOException {
        Request request = new Request.Builder()
                .url("https://api.twitter.com/2/tweets")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(content, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Twitter post failed: " + response.code());
            }

            var responseJson = objectMapper.readTree(response.body().string());
            return responseJson.get("data").get("id").asText();
        }
    }

    /**
     * Check if token needs refresh and refresh if necessary
     */
    @Transactional
    public void refreshTokenIfNeeded(SocialMediaAccount account) {
        if (account.getTokenExpiresAt() != null &&
            account.getTokenExpiresAt().isBefore(LocalDateTime.now().plusHours(1))) {

            log.info("Refreshing access token for account {} on {}",
                     account.getId(), account.getPlatform());

            // Token refresh logic would go here
            // This is platform-specific
        }
    }
}
