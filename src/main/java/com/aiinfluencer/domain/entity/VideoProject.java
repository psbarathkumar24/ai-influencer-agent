package com.aiinfluencer.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "video_projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 5000)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_style")
    private VideoStyle videoStyle;

    @Column(name = "target_duration_seconds")
    private Integer targetDurationSeconds;

    @Column(name = "script_content", length = 10000)
    private String scriptContent;

    @Column(name = "voice_audio_url")
    private String voiceAudioUrl;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "render_job_id")
    private String renderJobId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "videoProject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PublicationRecord> publicationRecords = new ArrayList<>();

    public enum Status {
        PENDING,
        GENERATING_SCRIPT,
        SCRIPT_GENERATED,
        GENERATING_VOICE,
        VOICE_GENERATED,
        RENDERING_VIDEO,
        VIDEO_READY,
        PUBLISHED,
        FAILED
    }

    public enum VideoStyle {
        TALKING_HEAD,
        ANIMATED,
        SCREEN_RECORDING,
        SLIDESHOW,
        DOCUMENTARY
    }
}
