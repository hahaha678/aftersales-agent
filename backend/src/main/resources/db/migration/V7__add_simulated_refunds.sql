ALTER TABLE aftersale_request
    DROP CHECK ck_aftersale_status,
    DROP CHECK ck_aftersale_return_fields,
    DROP CHECK ck_aftersale_receipt_fields,
    ADD CONSTRAINT ck_aftersale_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','RETURN_SHIPPED','RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED')),
    ADD CONSTRAINT ck_aftersale_return_fields CHECK (
        status NOT IN ('RETURN_SHIPPED','RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED') OR
        (return_carrier IS NOT NULL AND return_tracking_number IS NOT NULL AND return_registered_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_aftersale_receipt_fields CHECK (
        status NOT IN ('RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED') OR
        (received_at IS NOT NULL AND receipt_note IS NOT NULL)
    );

ALTER TABLE aftersale_event
    DROP CHECK ck_aftersale_event_action,
    ADD CONSTRAINT ck_aftersale_event_action CHECK (action IN ('SUBMITTED','APPROVED','REJECTED','CANCELLED','RETURN_SHIPPED','RETURN_RECEIVED','REFUND_PENDING','REFUND_FAILED','COMPLETED'));

-- 模拟渠道流水与业务状态在同一事务落库；不代表真实支付渠道接入。
CREATE TABLE aftersale_refund (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    request_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    operation_number VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    mode VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_refund_request_key (request_id, request_key),
    UNIQUE KEY uk_refund_operation (operation_number),
    KEY idx_refund_request (request_id, id),
    CONSTRAINT fk_refund_request FOREIGN KEY (request_id) REFERENCES aftersale_request(id),
    CONSTRAINT fk_refund_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_mode CHECK (mode IN ('SUCCESS','FAILURE','TIMEOUT_SUCCESS','TIMEOUT_FAILURE')),
    CONSTRAINT ck_refund_status CHECK (status IN ('SUCCEEDED','FAILED','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
