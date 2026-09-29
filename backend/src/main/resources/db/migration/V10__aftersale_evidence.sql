CREATE TABLE aftersale_evidence (
    id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    request_id BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    byte_size INT NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_evidence_content (request_id, sha256),
    CONSTRAINT fk_evidence_request FOREIGN KEY (request_id) REFERENCES aftersale_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
