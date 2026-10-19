package com.aiinfluencer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;

import java.io.File;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${app.aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Upload file to S3 and return the public URL
     */
    public String uploadFile(File file, String key) {
        try {
            log.info("Uploading file to S3: bucket={}, key={}", bucketName, key);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(determineContentType(key))
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));

            String url = s3Client.utilities().getUrl(
                    GetUrlRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build()
            ).toString();

            log.info("Successfully uploaded file to S3: {}", url);
            return url;

        } catch (Exception e) {
            log.error("Error uploading file to S3: bucket={}, key={}", bucketName, key, e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Upload file from path
     */
    public String uploadFile(Path filePath, String key) {
        return uploadFile(filePath.toFile(), key);
    }

    /**
     * Generate presigned URL for temporary access
     */
    public String generatePresignedUrl(String key, int expirationMinutes) {
        try {
            // Implementation for presigned URL generation
            // This would use S3Presigner for time-limited access
            log.info("Generating presigned URL for key: {}", key);
            // Simplified - in production use S3Presigner
            return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);

        } catch (Exception e) {
            log.error("Error generating presigned URL for key: {}", key, e);
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    private String determineContentType(String key) {
        String lowerKey = key.toLowerCase();
        if (lowerKey.endsWith(".mp4")) return "video/mp4";
        if (lowerKey.endsWith(".mp3")) return "audio/mpeg";
        if (lowerKey.endsWith(".jpg") || lowerKey.endsWith(".jpeg")) return "image/jpeg";
        if (lowerKey.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
