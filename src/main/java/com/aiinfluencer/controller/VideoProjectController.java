package com.aiinfluencer.controller;

import com.aiinfluencer.domain.entity.VideoProject;
import com.aiinfluencer.repository.VideoProjectRepository;
import com.aiinfluencer.service.VideoOrchestrationService;
import com.aiinfluencer.service.SocialMediaPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
public class VideoProjectController {

    private final VideoProjectRepository projectRepository;
    private final VideoOrchestrationService orchestrationService;
    private final SocialMediaPublishingService publishingService;

    /**
     * Create a new video project and start orchestration
     */
    @PostMapping
    public ResponseEntity<VideoProject> createProject(
            @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        try {
            log.info("Creating new video project: {}", request.title());

            Long userId = getUserIdFromPrincipal(principal);

            VideoProject project = VideoProject.builder()
                    .user(null) // Would set from UserService
                    .title(request.title())
                    .description(request.description())
                    .prompt(request.prompt())
                    .videoStyle(request.videoStyle())
                    .targetDurationSeconds(request.targetDurationSeconds())
                    .status(VideoProject.Status.PENDING)
                    .build();

            project = projectRepository.save(project);

            // Start async orchestration
            orchestrationService.orchestrateVideoCreation(project.getId());

            log.info("Video project created with ID: {}", project.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(project);

        } catch (Exception e) {
            log.error("Error creating video project", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all projects for authenticated user
     */
    @GetMapping
    public ResponseEntity<List<VideoProject>> getUserProjects(
            @AuthenticationPrincipal OAuth2User principal) {

        try {
            Long userId = getUserIdFromPrincipal(principal);
            List<VideoProject> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId);
            return ResponseEntity.ok(projects);

        } catch (Exception e) {
            log.error("Error fetching user projects", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get specific project by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<VideoProject> getProject(@PathVariable Long id) {
        return projectRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retry failed project
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<String> retryProject(@PathVariable Long id) {
        try {
            orchestrationService.retryProject(id);
            return ResponseEntity.ok("Project retry initiated");
        } catch (Exception e) {
            log.error("Error retrying project {}", id, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Publish video to social media
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<String> publishVideo(
            @PathVariable Long id,
            @RequestBody PublishRequest request) {

        try {
            VideoProject project = projectRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            if (project.getStatus() != VideoProject.Status.VIDEO_READY &&
                project.getStatus() != VideoProject.Status.PUBLISHED) {
                return ResponseEntity.badRequest()
                        .body("Video is not ready for publishing");
            }

            publishingService.publishVideo(id, request.socialMediaAccountId(), project);

            return ResponseEntity.ok("Publishing initiated");

        } catch (Exception e) {
            log.error("Error publishing video project {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    /**
     * Delete project
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        try {
            projectRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting project {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Long getUserIdFromPrincipal(OAuth2User principal) {
        // Extract user ID from OAuth2 principal
        // This would be populated by your security configuration
        return 1L; // Simplified
    }

    public record CreateProjectRequest(
            String title,
            String description,
            String prompt,
            VideoProject.VideoStyle videoStyle,
            Integer targetDurationSeconds
    ) {}

    public record PublishRequest(
            Long socialMediaAccountId
    ) {}
}
