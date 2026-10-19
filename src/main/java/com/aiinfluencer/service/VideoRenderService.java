package com.aiinfluencer.service;

import com.aiinfluencer.domain.entity.VideoProject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoRenderService {

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.video.renderer.api-url}")
    private String rendererApiUrl;

    @Value("${server.port}")
    private String serverPort;

    /**
     * Initiate video rendering with external service
     * Returns a job ID for tracking
     */
    public String initiateRendering(VideoProject project) {
        try {
            String jobId = UUID.randomUUID().toString();

            log.info("Initiating video rendering for project ID: {} with job ID: {}",
                     project.getId(), jobId);

            // Build rendering request payload
            var payload = objectMapper.createObjectNode()
                    .put("jobId", jobId)
                    .put("projectId", project.getId())
                    .put("scriptContent", project.getScriptContent())
                    .put("voiceAudioUrl", project.getVoiceAudioUrl())
                    .put("videoStyle", project.getVideoStyle() != null ?
                         project.getVideoStyle().name() : "TALKING_HEAD")
                    .put("durationSeconds", project.getTargetDurationSeconds() != null ?
                         project.getTargetDurationSeconds() : 60)
                    .put("webhookUrl", buildWebhookUrl());

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(payload),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(rendererApiUrl + "/render")
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Video renderer API error: {} - {}", response.code(), errorBody);
                    throw new IOException("Video renderer API request failed: " + response.code());
                }

                log.info("Successfully initiated video rendering. Job ID: {}", jobId);
                return jobId;
            }

        } catch (Exception e) {
            log.error("Error initiating video rendering for project ID: {}", project.getId(), e);
            throw new RuntimeException("Failed to initiate video rendering: " + e.getMessage(), e);
        }
    }

    private String buildWebhookUrl() {
        // In production, this would be your public domain
        // For now, using localhost for development
        return String.format("http://localhost:%s/api/webhooks/video-render", serverPort);
    }

    /**
     * Check rendering status (polling fallback)
     */
    public RenderStatus checkRenderingStatus(String jobId) {
        try {
            Request request = new Request.Builder()
                    .url(rendererApiUrl + "/status/" + jobId)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Failed to check rendering status");
                }

                return objectMapper.readValue(response.body().string(), RenderStatus.class);
            }

        } catch (Exception e) {
            log.error("Error checking rendering status for job ID: {}", jobId, e);
            throw new RuntimeException("Failed to check rendering status: " + e.getMessage(), e);
        }
    }

    public record RenderStatus(
            String jobId,
            String status,
            Integer progress,
            String videoUrl,
            String thumbnailUrl,
            String error
    ) {}
}
