ALTER TABLE agent_tool_call
    ADD COLUMN call_index INT NULL,
    ADD COLUMN started_at DATETIME(3) NULL,
    ADD COLUMN input_summary VARCHAR(1200) NULL,
    ADD COLUMN result_summary VARCHAR(1200) NULL,
    ADD COLUMN error_code VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_run_call_index(run_id,call_index);
CREATE INDEX idx_agent_monitor_time ON agent_run(created_at,id);
CREATE INDEX idx_agent_monitor_status_time ON agent_run(status,created_at,id);
