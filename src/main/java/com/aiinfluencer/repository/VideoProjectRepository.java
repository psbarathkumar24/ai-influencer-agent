package com.aiinfluencer.repository;

import com.aiinfluencer.domain.entity.VideoProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoProjectRepository extends JpaRepository<VideoProject, Long> {
    List<VideoProject> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<VideoProject> findByStatus(VideoProject.Status status);
    Optional<VideoProject> findByRenderJobId(String renderJobId);

    @Query("SELECT vp FROM VideoProject vp WHERE vp.status IN " +
           "('GENERATING_SCRIPT', 'GENERATING_VOICE', 'RENDERING_VIDEO')")
    List<VideoProject> findInProgressProjects();
}
