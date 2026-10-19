package com.aiinfluencer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElevenLabsVoiceService {

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3StorageService s3StorageService;

    @Value("${app.elevenlabs.api-key}")
    private String apiKey;

    @Value("${app.elevenlabs.voice-id}")
    private String voiceId;

    @Value("${app.elevenlabs.model}")
    private String model;

    private static final String ELEVENLABS_API_URL = "https://api.elevenlabs.io/v1";

    /**
     * Generate voice audio from script text using ElevenLabs API
     */
    public String generateVoice(Long projectId, String scriptText) {
        try {
            log.info("Generating voice for project ID: {} using voice ID: {}", projectId, voiceId);

            String audioData = callElevenLabsApi(scriptText);

            // Save to temporary file
            Path tempFile = Files.createTempFile("voice-" + projectId + "-", ".mp3");
            Files.write(tempFile, audioData.getBytes());

            // Upload to S3
            String s3Key = String.format("audio/project-%d/%s.mp3", projectId, UUID.randomUUID());
            String s3Url = s3StorageService.uploadFile(tempFile.toFile(), s3Key);

            // Cleanup temp file
            Files.deleteIfExists(tempFile);

            log.info("Successfully generated and uploaded voice for project ID: {}. S3 URL: {}",
                     projectId, s3Url);

            return s3Url;

        } catch (Exception e) {
            log.error("Error generating voice for project ID: {}", projectId, e);
            throw new RuntimeException("Failed to generate voice: " + e.getMessage(), e);
        }
    }

    private String callElevenLabsApi(String text) throws IOException {
        String url = String.format("%s/text-to-speech/%s", ELEVENLABS_API_URL, voiceId);

        JsonNode requestBody = objectMapper.createObjectNode()
                .put("text", text)
                .put("model_id", model)
                .set("voice_settings", objectMapper.createObjectNode()
                        .put("stability", 0.5)
                        .put("similarity_boost", 0.75));

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("ElevenLabs API error: {} - {}", response.code(), errorBody);
                throw new IOException("ElevenLabs API request failed: " + response.code());
            }

            if (response.body() == null) {
                throw new IOException("Empty response from ElevenLabs API");
            }

            return response.body().string();
        }
    }

    /**
     * List available voices from ElevenLabs
     */
    public JsonNode listVoices() {
        try {
            Request request = new Request.Builder()
                    .url(ELEVENLABS_API_URL + "/voices")
                    .header("xi-api-key", apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Failed to fetch voices");
                }

                return objectMapper.readTree(response.body().string());
            }

        } catch (Exception e) {
            log.error("Error listing voices", e);
            throw new RuntimeException("Failed to list voices: " + e.getMessage(), e);
        }
    }
}
