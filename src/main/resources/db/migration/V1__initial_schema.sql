-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    profile_picture_url VARCHAR(500),
    provider VARCHAR(50) NOT NULL,
    provider_id VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_provider ON users(provider, provider_id);

-- User roles table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Social media accounts table
CREATE TABLE social_media_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    platform_user_id VARCHAR(255) NOT NULL,
    platform_username VARCHAR(255),
    access_token TEXT,
    refresh_token TEXT,
    token_expires_at TIMESTAMP,
    is_connected BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, platform)
);

CREATE INDEX idx_social_accounts_user ON social_media_accounts(user_id);
CREATE INDEX idx_social_accounts_platform ON social_media_accounts(platform);

-- Video projects table
CREATE TABLE video_projects (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    prompt TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    video_style VARCHAR(50),
    target_duration_seconds INTEGER,
    script_content TEXT,
    voice_audio_url VARCHAR(1000),
    video_url VARCHAR(1000),
    thumbnail_url VARCHAR(1000),
    error_message TEXT,
    render_job_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_video_projects_user ON video_projects(user_id);
CREATE INDEX idx_video_projects_status ON video_projects(status);
CREATE INDEX idx_video_projects_render_job ON video_projects(render_job_id);
CREATE INDEX idx_video_projects_created ON video_projects(created_at DESC);

-- Publication records table
CREATE TABLE publication_records (
    id BIGSERIAL PRIMARY KEY,
    video_project_id BIGINT NOT NULL,
    social_media_account_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    platform_post_id VARCHAR(255),
    platform_url VARCHAR(1000),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    FOREIGN KEY (video_project_id) REFERENCES video_projects(id) ON DELETE CASCADE,
    FOREIGN KEY (social_media_account_id) REFERENCES social_media_accounts(id) ON DELETE CASCADE
);

CREATE INDEX idx_publication_records_project ON publication_records(video_project_id);
CREATE INDEX idx_publication_records_account ON publication_records(social_media_account_id);
CREATE INDEX idx_publication_records_status ON publication_records(status);
