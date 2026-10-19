package com.aiinfluencer.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "publication_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_project_id", nullable = false)
    private VideoProject videoProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_media_account_id", nullable = false)
    private SocialMediaAccount socialMediaAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "platform_post_id")
    private String platformPostId;

    @Column(name = "platform_url", length = 1000)
    private String platformUrl;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public enum Status {
        PENDING,
        IN_PROGRESS,
        PUBLISHED,
        FAILED
    }
}
