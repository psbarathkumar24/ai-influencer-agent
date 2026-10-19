package com.aiinfluencer.service;

import com.aiinfluencer.domain.entity.VideoProject;
import com.aiinfluencer.repository.VideoProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoOrchestrationService {

    private final VideoProjectRepository videoProjectRepository;
    private final OpenAiScriptService scriptService;
    private final ElevenLabsVoiceService voiceService;
    private final VideoRenderService renderService;

    /**
     * Orchestrate the complete video creation pipeline asynchronously
     */
    @Async
    @Transactional
    public void orchestrateVideoCreation(Long projectId) {
        try {
            log.info("Starting video orchestration for project ID: {}", projectId);

            VideoProject project = videoProjectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            // Step 1: Generate Script
            generateScript(project);

            // Step 2: Generate Voice
            generateVoice(project);

            // Step 3: Render Video (async webhook-based)
            initiateVideoRendering(project);

            log.info("Video orchestration initiated successfully for project ID: {}", projectId);

        } catch (Exception e) {
            log.error("Error in video orchestration for project ID: {}", projectId, e);
            handleOrchestrationError(projectId, e.getMessage());
        }
    }

    @Transactional
    public void generateScript(VideoProject project) {
        try {
            log.info("Generating script for project ID: {}", project.getId());
            project.setStatus(VideoProject.Status.GENERATING_SCRIPT);
            videoProjectRepository.save(project);

            String script = scriptService.generateScript(project);

            project.setScriptContent(script);
            project.setStatus(VideoProject.Status.SCRIPT_GENERATED);
            videoProjectRepository.save(project);

            log.info("Script generated successfully for project ID: {}", project.getId());

        } catch (Exception e) {
            log.error("Error generating script for project ID: {}", project.getId(), e);
            throw new RuntimeException("Script generation failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void generateVoice(VideoProject project) {
        try {
            log.info("Generating voice for project ID: {}", project.getId());
            project.setStatus(VideoProject.Status.GENERATING_VOICE);
            videoProjectRepository.save(project);

            String voiceUrl = voiceService.generateVoice(project.getId(), project.getScriptContent());

            project.setVoiceAudioUrl(voiceUrl);
            project.setStatus(VideoProject.Status.VOICE_GENERATED);
            videoProjectRepository.save(project);

            log.info("Voice generated successfully for project ID: {}", project.getId());

        } catch (Exception e) {
            log.error("Error generating voice for project ID: {}", project.getId(), e);
            throw new RuntimeException("Voice generation failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void initiateVideoRendering(VideoProject project) {
        try {
            log.info("Initiating video rendering for project ID: {}", project.getId());
            project.setStatus(VideoProject.Status.RENDERING_VIDEO);
            videoProjectRepository.save(project);

            String renderJobId = renderService.initiateRendering(project);

            project.setRenderJobId(renderJobId);
            videoProjectRepository.save(project);

            log.info("Video rendering initiated with job ID: {} for project ID: {}",
                     renderJobId, project.getId());

        } catch (Exception e) {
            log.error("Error initiating video rendering for project ID: {}", project.getId(), e);
            throw new RuntimeException("Video rendering initiation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Called by webhook when video rendering completes
     */
    @Transactional
    public void handleRenderingComplete(String renderJobId, String videoUrl, String thumbnailUrl) {
        try {
            log.info("Processing rendering completion for job ID: {}", renderJobId);

            VideoProject project = videoProjectRepository.findByRenderJobId(renderJobId)
                    .orElseThrow(() -> new RuntimeException("Project not found for job ID: " + renderJobId));

            project.setVideoUrl(videoUrl);
            project.setThumbnailUrl(thumbnailUrl);
            project.setStatus(VideoProject.Status.VIDEO_READY);
            project.setCompletedAt(LocalDateTime.now());
            videoProjectRepository.save(project);

            log.info("Video rendering completed successfully for project ID: {}", project.getId());

        } catch (Exception e) {
            log.error("Error handling rendering completion for job ID: {}", renderJobId, e);
            throw new RuntimeException("Failed to process rendering completion: " + e.getMessage(), e);
        }
    }

    /**
     * Called by webhook when video rendering fails
     */
    @Transactional
    public void handleRenderingFailure(String renderJobId, String errorMessage) {
        try {
            log.error("Processing rendering failure for job ID: {}", renderJobId);

            VideoProject project = videoProjectRepository.findByRenderJobId(renderJobId)
                    .orElseThrow(() -> new RuntimeException("Project not found for job ID: " + renderJobId));

            project.setStatus(VideoProject.Status.FAILED);
            project.setErrorMessage("Video rendering failed: " + errorMessage);
            videoProjectRepository.save(project);

            log.error("Video rendering failed for project ID: {}. Error: {}",
                     project.getId(), errorMessage);

        } catch (Exception e) {
            log.error("Error handling rendering failure for job ID: {}", renderJobId, e);
        }
    }

    private void handleOrchestrationError(Long projectId, String errorMessage) {
        try {
            VideoProject project = videoProjectRepository.findById(projectId).orElse(null);
            if (project != null) {
                project.setStatus(VideoProject.Status.FAILED);
                project.setErrorMessage(errorMessage);
                videoProjectRepository.save(project);
            }
        } catch (Exception e) {
            log.error("Error updating project status after orchestration failure", e);
        }
    }

    /**
     * Retry failed project
     */
    @Transactional
    public void retryProject(Long projectId) {
        VideoProject project = videoProjectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        if (project.getStatus() != VideoProject.Status.FAILED) {
            throw new RuntimeException("Can only retry failed projects");
        }

        project.setStatus(VideoProject.Status.PENDING);
        project.setErrorMessage(null);
        videoProjectRepository.save(project);

        orchestrateVideoCreation(projectId);
    }
}
