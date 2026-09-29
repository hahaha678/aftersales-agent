CREATE TABLE support_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    conversation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    problem VARCHAR(2000) NOT NULL,
    order_id BIGINT NULL,
    aftersale_id BIGINT NULL,
    context_end_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    assignee_id BIGINT NULL,
    resolution VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    active_conversation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
        (CASE WHEN status <> 'RESOLVED' THEN conversation_id ELSE NULL END) STORED,
    UNIQUE KEY uk_ticket_active (active_conversation),
    UNIQUE KEY uk_ticket_request (user_id, request_key),
    KEY idx_ticket_owner (user_id, id),
    KEY idx_ticket_status (status, id),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (conversation_id) REFERENCES conversation(id),
    FOREIGN KEY (order_id) REFERENCES trade_order(id),
    FOREIGN KEY (aftersale_id) REFERENCES aftersale_request(id),
    FOREIGN KEY (assignee_id) REFERENCES app_user(id),
    CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
    CHECK (status='OPEN' OR assignee_id IS NOT NULL),
    CHECK (status<>'RESOLVED' OR resolution IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_ticket_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    note VARCHAR(2000) NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_ticket_event (ticket_id, id),
    FOREIGN KEY (ticket_id) REFERENCES support_ticket(id),
    FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CHECK (action IN ('OPEN','IN_PROGRESS','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 复用未关闭工单时也保存请求键，防止原请求延迟重试在解决后另建工单。
CREATE TABLE support_ticket_submission (
    user_id BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ticket_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, request_key),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (ticket_id) REFERENCES support_ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE support_ticket_context (
    ticket_id BIGINT NOT NULL,
    id BIGINT NOT NULL,
    conversation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (ticket_id,id),
    FOREIGN KEY (ticket_id) REFERENCES support_ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
