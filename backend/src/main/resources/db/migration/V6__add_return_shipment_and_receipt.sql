-- 保留历史 V3 校验和；只扩展售后状态及单次退回信息。
ALTER TABLE aftersale_request
    ADD COLUMN return_carrier VARCHAR(40) NULL,
    ADD COLUMN return_tracking_number VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN return_registered_at DATETIME(3) NULL,
    ADD COLUMN received_at DATETIME(3) NULL,
    ADD COLUMN receipt_note VARCHAR(1000) NULL,
    DROP CHECK ck_aftersale_status,
    ADD CONSTRAINT ck_aftersale_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','RETURN_SHIPPED','RETURN_RECEIVED')),
    ADD CONSTRAINT ck_aftersale_return_fields CHECK (
        status NOT IN ('RETURN_SHIPPED','RETURN_RECEIVED') OR
        (return_carrier IS NOT NULL AND return_tracking_number IS NOT NULL AND return_registered_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_aftersale_receipt_fields CHECK (
        status <> 'RETURN_RECEIVED' OR (received_at IS NOT NULL AND receipt_note IS NOT NULL)
    );

ALTER TABLE aftersale_event
    DROP CHECK ck_aftersale_event_action,
    ADD CONSTRAINT ck_aftersale_event_action CHECK (action IN ('SUBMITTED','APPROVED','REJECTED','CANCELLED','RETURN_SHIPPED','RETURN_RECEIVED'));
