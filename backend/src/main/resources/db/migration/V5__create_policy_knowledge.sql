CREATE TABLE knowledge_policy_lock (
    policy_key VARCHAR(64) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    PRIMARY KEY (policy_key, scope)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_policy (
    id CHAR(36) PRIMARY KEY,
    policy_key VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE NOT NULL,
    effective_until DATE NOT NULL,
    embedding_identity VARCHAR(64),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    active_key VARCHAR(140) GENERATED ALWAYS AS
        (CASE WHEN status = 'PUBLISHED' THEN CONCAT(policy_key, ':', scope) ELSE NULL END) STORED,
    UNIQUE KEY uk_policy_version (policy_key, scope, version),
    UNIQUE KEY uk_active_policy (active_key)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_run_source (
    run_id CHAR(36) NOT NULL,
    source_id CHAR(36) NOT NULL,
    source_json JSON NOT NULL,
    PRIMARY KEY (run_id, source_id)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_chunk (
    id CHAR(36) PRIMARY KEY,
    policy_id CHAR(36) NOT NULL,
    ordinal INT NOT NULL,
    content TEXT NOT NULL,
    embedding JSON NOT NULL,
    CONSTRAINT fk_chunk_policy FOREIGN KEY (policy_id) REFERENCES knowledge_policy(id),
    UNIQUE KEY uk_policy_chunk (policy_id, ordinal)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
