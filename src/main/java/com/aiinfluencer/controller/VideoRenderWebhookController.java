package com.aiinfluencer.controller;

import com.aiinfluencer.service.VideoOrchestrationService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class VideoRenderWebhookController {

    private final VideoOrchestrationService orchestrationService;

    @Value("${app.video.renderer.webhook-secret}")
    private String webhookSecret;

    /**
     * Webhook endpoint for video rendering completion/failure callbacks
     */
    @PostMapping("/video-render")
    public ResponseEntity<String> handleVideoRenderCallback(
            @RequestBody JsonNode payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        try {
            log.info("Received video render webhook callback: {}", payload);

            // Verify webhook signature for security
            if (!verifySignature(payload.toString(), signature)) {
                log.warn("Invalid webhook signature received");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Invalid signature");
            }

            String jobId = payload.get("jobId").asText();
            String status = payload.get("status").asText();

            switch (status.toUpperCase()) {
                case "COMPLETED" -> {
                    String videoUrl = payload.get("videoUrl").asText();
                    String thumbnailUrl = payload.get("thumbnailUrl").asText();
                    orchestrationService.handleRenderingComplete(jobId, videoUrl, thumbnailUrl);
                    log.info("Successfully processed rendering completion for job ID: {}", jobId);
                }
                case "FAILED" -> {
                    String errorMessage = payload.has("error") ?
                            payload.get("error").asText() : "Unknown error";
                    orchestrationService.handleRenderingFailure(jobId, errorMessage);
                    log.error("Successfully processed rendering failure for job ID: {}", jobId);
                }
                default -> {
                    log.warn("Unknown status received in webhook: {}", status);
                    return ResponseEntity.badRequest().body("Unknown status: " + status);
                }
            }

            return ResponseEntity.ok("Webhook processed successfully");

        } catch (Exception e) {
            log.error("Error processing video render webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Verify HMAC signature for webhook security
     */
    private boolean verifySignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("No signature provided in webhook request");
            return false;
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            hmac.init(secretKey);

            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = HexFormat.of().formatHex(hash);

            return calculatedSignature.equals(signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Health check endpoint for webhook service
     */
    @GetMapping("/video-render/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Webhook endpoint is healthy");
    }
}
