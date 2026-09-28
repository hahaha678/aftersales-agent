-- Initial migration verifies the database migration pipeline, not business tables.
CREATE TABLE project_metadata (
    metadata_key VARCHAR(64) NOT NULL PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO project_metadata (metadata_key, metadata_value)
VALUES ('project_name', 'aftersales-agent');
