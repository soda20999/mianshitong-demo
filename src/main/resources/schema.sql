CREATE TABLE IF NOT EXISTS xz_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(128) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar LONGTEXT NOT NULL,
    target_position VARCHAR(128),
    points INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    role VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_xz_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_resume (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    version VARCHAR(32) NOT NULL,
    file_sha256 CHAR(64),
    cache_hit TINYINT(1) NOT NULL DEFAULT 0,
    content LONGTEXT NOT NULL,
    parse_result JSON,
    uploaded_at DATETIME NOT NULL,
    KEY idx_xz_resume_user_id (user_id),
    KEY idx_xz_resume_file_sha256 (file_sha256),
    KEY idx_xz_resume_uploaded_at (uploaded_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_jd_analysis (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_title VARCHAR(128) NOT NULL,
    jd_content LONGTEXT NOT NULL,
    keywords JSON,
    core_skills JSON,
    interview_focuses JSON,
    suggestions JSON,
    created_at DATETIME NOT NULL,
    KEY idx_xz_jd_analysis_user_id (user_id),
    KEY idx_xz_jd_analysis_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_question_set (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    job_title VARCHAR(128) NOT NULL,
    direction VARCHAR(64) NOT NULL,
    level VARCHAR(64) NOT NULL,
    company_style VARCHAR(128),
    questions JSON NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_xz_question_set_user_id (user_id),
    KEY idx_xz_question_set_resume_id (resume_id),
    KEY idx_xz_question_set_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_interview_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_set_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    style VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    messages JSON,
    score_history JSON,
    favorite_question_ids JSON,
    wrong_question_ids JSON,
    total_score INT,
    created_at DATETIME NOT NULL,
    finished_at DATETIME,
    KEY idx_xz_interview_session_user_id (user_id),
    KEY idx_xz_interview_session_question_set_id (question_set_id),
    KEY idx_xz_interview_session_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    interview_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    overall_score INT,
    dimensions JSON,
    weak_points JSON,
    review_roadmap JSON,
    question_list JSON,
    user_answer_highlights JSON,
    ai_standard_answers JSON,
    bright_spots JSON,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_xz_report_user_id (user_id),
    KEY idx_xz_report_interview_id (interview_id),
    KEY idx_xz_report_status (status),
    KEY idx_xz_report_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_hot_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    bank_file_id BIGINT,
    position VARCHAR(128) NOT NULL,
    tag VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    answer TEXT,
    views BIGINT NOT NULL DEFAULT 0,
    favorites BIGINT NOT NULL DEFAULT 0,
    practices BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_xz_hot_question_user_id (user_id),
    KEY idx_xz_hot_question_bank_file_id (bank_file_id),
    KEY idx_xz_hot_question_tag (tag),
    KEY idx_xz_hot_question_position (position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_hot_question_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_xz_hot_question_favorite_user_question (user_id, question_id),
    KEY idx_xz_hot_question_favorite_question_id (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_question_bank_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    uploaded_at DATETIME NOT NULL,
    KEY idx_xz_question_bank_file_user_id (user_id),
    KEY idx_xz_question_bank_file_uploaded_at (uploaded_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    content LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_xz_prompt_template_module (module),
    KEY idx_xz_prompt_template_updated_at (updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_ai_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    module VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    cost DECIMAL(12, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    log_time DATETIME NOT NULL,
    KEY idx_xz_ai_log_user_id (user_id),
    KEY idx_xz_ai_log_module (module),
    KEY idx_xz_ai_log_time (log_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_risk_config (
    id BIGINT PRIMARY KEY,
    rate_limit_per_minute INT NOT NULL,
    max_question_generate_per_day INT NOT NULL,
    max_report_generate_per_day INT NOT NULL,
    upload_max_mb INT NOT NULL,
    upload_allow_types VARCHAR(255) NOT NULL,
    input_max_length INT NOT NULL,
    prompt_injection_check TINYINT(1) NOT NULL,
    output_safety_check TINYINT(1) NOT NULL,
    idempotency_check TINYINT(1) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xz_sensitive_word (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(128) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_xz_sensitive_word_word (word),
    KEY idx_xz_sensitive_word_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_user'
              AND COLUMN_NAME = 'avatar'
              AND DATA_TYPE = 'longtext'
        ),
        'SELECT 1',
        'ALTER TABLE xz_user MODIFY COLUMN avatar LONGTEXT NOT NULL'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_resume'
              AND COLUMN_NAME = 'file_sha256'
        ),
        'SELECT 1',
        'ALTER TABLE xz_resume ADD COLUMN file_sha256 CHAR(64)'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_resume'
              AND COLUMN_NAME = 'cache_hit'
        ),
        'SELECT 1',
        'ALTER TABLE xz_resume ADD COLUMN cache_hit TINYINT(1) NOT NULL DEFAULT 0'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_resume'
              AND INDEX_NAME = 'idx_xz_resume_file_sha256'
        ),
        'SELECT 1',
        'ALTER TABLE xz_resume ADD INDEX idx_xz_resume_file_sha256 (file_sha256)'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_hot_question'
              AND COLUMN_NAME = 'user_id'
        ),
        'SELECT 1',
        'ALTER TABLE xz_hot_question ADD COLUMN user_id BIGINT'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_hot_question'
              AND COLUMN_NAME = 'bank_file_id'
        ),
        'SELECT 1',
        'ALTER TABLE xz_hot_question ADD COLUMN bank_file_id BIGINT'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_hot_question'
              AND COLUMN_NAME = 'answer'
        ),
        'SELECT 1',
        'ALTER TABLE xz_hot_question ADD COLUMN answer TEXT'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_hot_question'
              AND COLUMN_NAME = 'created_at'
        ),
        'SELECT 1',
        'ALTER TABLE xz_hot_question ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_hot_question'
              AND INDEX_NAME = 'idx_xz_hot_question_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE xz_hot_question ADD INDEX idx_xz_hot_question_user_id (user_id)'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'xz_hot_question'
              AND INDEX_NAME = 'idx_xz_hot_question_bank_file_id'
        ),
        'SELECT 1',
        'ALTER TABLE xz_hot_question ADD INDEX idx_xz_hot_question_bank_file_id (bank_file_id)'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
