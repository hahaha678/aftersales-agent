-- MySQL 8.0.43. All DATETIME values represent UTC; API timestamps carry an offset.
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    username VARCHAR(64) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '登录名，区分大小写',
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '带算法标识的密码摘要，不保存明文',
    display_name VARCHAR(64) NOT NULL COMMENT '展示名称',
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CUSTOMER / STAFF',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否允许登录及使用已有会话',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT ck_app_user_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户及客服账号';

CREATE TABLE user_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部会话 ID，不作为访问凭据',
    user_id BIGINT NOT NULL COMMENT '所属用户',
    token_hash BINARY(32) NOT NULL COMMENT '高熵随机令牌的 SHA-256 摘要',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '签发时间 UTC',
    expires_at DATETIME(3) NOT NULL COMMENT '绝对过期时间 UTC',
    revoked_at DATETIME(3) NULL COMMENT '撤销时间 UTC；保留记录至原过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_session_token (token_hash),
    KEY idx_user_session_user (user_id),
    KEY idx_user_session_expiry (expires_at),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_session_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_user_session_revoked CHECK (revoked_at IS NULL OR revoked_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可撤销的服务端 Bearer 会话';

CREATE TABLE trade_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    user_id BIGINT NOT NULL COMMENT '所属用户；查询必须限制该字段',
    order_number VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务订单号',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单状态，与售后状态分离',
    paid_amount DECIMAL(12,2) NOT NULL COMMENT '原始实付总额，退款后不改写',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY' COMMENT '币种',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下单时间 UTC',
    paid_at DATETIME(3) NULL COMMENT '支付时间 UTC',
    signed_at DATETIME(3) NULL COMMENT '签收时间 UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_order_number (order_number),
    KEY idx_trade_order_user_created (user_id, created_at DESC, id DESC),
    KEY idx_trade_order_user_status_created (user_id, status, created_at DESC, id DESC),
    CONSTRAINT fk_trade_order_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_trade_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_trade_order_amount CHECK (paid_amount >= 0),
    CONSTRAINT ck_trade_order_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_trade_order_payment CHECK (
        (status IN ('PENDING_PAYMENT', 'CANCELLED') AND paid_at IS NULL AND paid_amount = 0)
        OR (status IN ('PAID', 'SHIPPED', 'COMPLETED') AND paid_at IS NOT NULL AND paid_at >= created_at)
    ),
    CONSTRAINT ck_trade_order_signed CHECK (
        (status = 'COMPLETED' AND signed_at IS NOT NULL AND signed_at >= paid_at)
        OR (status <> 'COMPLETED' AND signed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始交易订单；首版仅支持未支付取消';

CREATE TABLE order_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单商品项 ID',
    order_id BIGINT NOT NULL COMMENT '所属订单',
    sku_id BIGINT NOT NULL COMMENT '外部 SKU 标识；当前不建立商品主表',
    product_name VARCHAR(200) NOT NULL COMMENT '下单时商品名称快照',
    specification VARCHAR(255) NOT NULL DEFAULT '' COMMENT '下单时规格快照',
    quantity INT NOT NULL COMMENT '购买数量',
    paid_amount DECIMAL(12,2) NOT NULL COMMENT '整行商品优惠后实付金额，不是单价',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id, id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES trade_order (id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_sku CHECK (sku_id > 0),
    CONSTRAINT ck_order_item_amount CHECK (paid_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品快照';

CREATE TABLE order_shipment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '原订单物流 ID',
    order_id BIGINT NOT NULL COMMENT '所属订单，首版最多一个原始包裹',
    carrier VARCHAR(64) NOT NULL COMMENT '承运商名称',
    tracking_number VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物流单号；演示数据使用模拟单号',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'IN_TRANSIT / DELIVERED / EXCEPTION',
    shipped_at DATETIME(3) NOT NULL COMMENT '发货时间 UTC',
    delivered_at DATETIME(3) NULL COMMENT '签收时间 UTC，与订单签收时间事务内同步',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_shipment_order (order_id),
    CONSTRAINT fk_order_shipment_order FOREIGN KEY (order_id) REFERENCES trade_order (id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_shipment_status CHECK (status IN ('IN_TRANSIT', 'DELIVERED', 'EXCEPTION')),
    CONSTRAINT ck_order_shipment_delivered CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL AND delivered_at >= shipped_at)
        OR (status <> 'DELIVERED' AND delivered_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原订单物流；售后寄回物流后续独立建表';

CREATE TABLE shipment_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物流轨迹 ID',
    shipment_id BIGINT NOT NULL COMMENT '所属物流',
    occurred_at DATETIME(3) NOT NULL COMMENT '事件发生时间 UTC',
    description VARCHAR(500) NOT NULL COMMENT '物流轨迹描述',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间 UTC',
    PRIMARY KEY (id),
    KEY idx_shipment_event_time (shipment_id, occurred_at, id),
    CONSTRAINT fk_shipment_event_shipment FOREIGN KEY (shipment_id) REFERENCES order_shipment (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹，按发生时间及 ID 排序';
