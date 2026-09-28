-- 开发演示数据：在 IDEA / Navicat 中直接运行整个文件，无需拼接 SQL。
-- 本文件自动选择 aftersales_agent 库并开启本次导入。
-- 前提：已通过 Spring Boot local 配置执行 Flyway V1、V2，创建业务表。
-- 仅用于本机开发库；首次要求业务表为空，重复导入会跳过，不覆盖已有数据。
-- 三个演示账号的公开密码均为 DemoPass123!，不得用于生产环境。
USE aftersales_agent;
SET @allow_demo_seed = 1;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DELIMITER $$
DROP PROCEDURE IF EXISTS seed_aftersales_demo_v1$$
CREATE PROCEDURE seed_aftersales_demo_v1()
seed: BEGIN
    DECLARE seed_lock INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF seed_lock = 1 THEN
            DO RELEASE_LOCK(CONCAT(DATABASE(), ':aftersales-demo-seed'));
        END IF;
        RESIGNAL;
    END;
    IF COALESCE(@allow_demo_seed, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Set @allow_demo_seed = 1 explicitly for a development database';
    END IF;
    SELECT GET_LOCK(CONCAT(DATABASE(), ':aftersales-demo-seed'), 5) INTO seed_lock;
    IF seed_lock IS NULL OR seed_lock <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot acquire demo seed lock';
    END IF;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM project_metadata WHERE metadata_key = 'demo_seed_version' AND metadata_value = '1') THEN
        COMMIT;
        DO RELEASE_LOCK(CONCAT(DATABASE(), ':aftersales-demo-seed'));
        SELECT 'Demo v1 already imported; existing data preserved' AS result;
        LEAVE seed;
    END IF;
    IF EXISTS (SELECT 1 FROM app_user) OR EXISTS (SELECT 1 FROM trade_order)
        OR EXISTS (SELECT 1 FROM user_session) OR EXISTS (SELECT 1 FROM order_item)
        OR EXISTS (SELECT 1 FROM order_shipment) OR EXISTS (SELECT 1 FROM shipment_event)
        OR EXISTS (SELECT 1 FROM project_metadata WHERE metadata_key = 'demo_seed_version') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo import requires empty business tables';
    END IF;

    -- PBKDF2-HMAC-SHA256, 600000 iterations, 16-byte salt, 32-byte key.
    -- {pbkdf2} + hex(salt || derived key). See DATABASE_DESIGN.md for encoder settings.
    SET @demo_hash = '{pbkdf2}4e33cdf6e04603b47b210789623049e7a052c02b3fd520506d562848c9c68a7cf060783cbf9060b8b2f05e25d2601c80';
    SET @demo_now = UTC_TIMESTAMP(3);
    INSERT INTO app_user (id, username, password_hash, display_name, role) VALUES
        (1, 'demo_customer', @demo_hash, '演示买家', 'CUSTOMER'),
        (2, 'demo_other', @demo_hash, '另一位买家', 'CUSTOMER'),
        (3, 'demo_staff', @demo_hash, '演示客服', 'STAFF');

    INSERT INTO trade_order (id, user_id, order_number, status, paid_amount, created_at, paid_at, signed_at) VALUES
        (1001, 1, 'DEMO-1001', 'COMPLETED', 199.00, @demo_now - INTERVAL 5 DAY, @demo_now - INTERVAL 5 DAY + INTERVAL 5 MINUTE, @demo_now - INTERVAL 2 DAY),
        (1002, 1, 'DEMO-1002', 'COMPLETED', 89.00, @demo_now - INTERVAL 40 DAY, @demo_now - INTERVAL 40 DAY + INTERVAL 5 MINUTE, @demo_now - INTERVAL 35 DAY),
        (1003, 1, 'DEMO-1003', 'SHIPPED', 258.00, @demo_now - INTERVAL 2 DAY, @demo_now - INTERVAL 2 DAY + INTERVAL 5 MINUTE, NULL),
        (1004, 1, 'DEMO-1004', 'PAID', 59.00, @demo_now - INTERVAL 1 DAY, @demo_now - INTERVAL 1 DAY + INTERVAL 5 MINUTE, NULL),
        (1005, 1, 'DEMO-1005', 'PENDING_PAYMENT', 0.00, @demo_now - INTERVAL 1 HOUR, NULL, NULL),
        (1006, 1, 'DEMO-1006', 'CANCELLED', 0.00, @demo_now - INTERVAL 3 DAY, NULL, NULL),
        (1007, 2, 'DEMO-1007', 'COMPLETED', 699.00, @demo_now - INTERVAL 8 DAY, @demo_now - INTERVAL 8 DAY + INTERVAL 5 MINUTE, @demo_now - INTERVAL 4 DAY);
    INSERT INTO order_item (id, order_id, sku_id, product_name, specification, quantity, paid_amount) VALUES
        (2001, 1001, 501, '无线鼠标', '黑色', 1, 129.00),
        (2002, 1001, 502, '鼠标垫', '灰色 / 大号', 2, 70.00),
        (2003, 1002, 503, '保温杯', '银色 / 500ml', 1, 89.00),
        (2004, 1003, 501, '无线鼠标', '黑色', 2, 258.00),
        (2005, 1004, 504, '手机支架', '白色', 1, 59.00),
        (2006, 1005, 505, '键盘', '白色 / 87键', 1, 0.00),
        (2007, 1006, 506, '数据线', '1米', 1, 0.00),
        (2008, 1007, 507, '显示器', '24英寸', 1, 699.00);
    INSERT INTO order_shipment (id, order_id, carrier, tracking_number, status, shipped_at, delivered_at) VALUES
        (3001, 1001, '模拟物流', 'MOCK-3001', 'DELIVERED', @demo_now - INTERVAL 4 DAY, @demo_now - INTERVAL 2 DAY),
        (3002, 1002, '模拟物流', 'MOCK-3002', 'DELIVERED', @demo_now - INTERVAL 39 DAY, @demo_now - INTERVAL 35 DAY),
        (3003, 1003, '模拟物流', 'MOCK-3003', 'IN_TRANSIT', @demo_now - INTERVAL 1 DAY, NULL),
        (3004, 1007, '模拟物流', 'MOCK-3004', 'DELIVERED', @demo_now - INTERVAL 7 DAY, @demo_now - INTERVAL 4 DAY);
    INSERT INTO shipment_event (shipment_id, occurred_at, description)
        SELECT id, shipped_at, '模拟轨迹：包裹已揽收' FROM order_shipment;
    INSERT INTO shipment_event (shipment_id, occurred_at, description)
        SELECT id, delivered_at, '模拟轨迹：包裹已签收' FROM order_shipment WHERE delivered_at IS NOT NULL;
    INSERT INTO shipment_event (shipment_id, occurred_at, description)
        VALUES (3003, @demo_now - INTERVAL 6 HOUR, '模拟轨迹：运输途中');
    INSERT INTO project_metadata (metadata_key, metadata_value) VALUES ('demo_seed_version', '1');
    COMMIT;
    DO RELEASE_LOCK(CONCAT(DATABASE(), ':aftersales-demo-seed'));
    SET @demo_hash = NULL;
    SELECT 'Demo v1 imported: 3 users, 7 orders, 8 items, 4 shipments, 8 events' AS result;
END$$
DELIMITER ;
CALL seed_aftersales_demo_v1();
DROP PROCEDURE seed_aftersales_demo_v1;
SET @allow_demo_seed = NULL;

-- 执行结束后展示已导入账号，便于确认结果（不查询密码摘要）。
SELECT id, username, display_name, role
FROM app_user
WHERE username IN ('demo_customer', 'demo_other', 'demo_staff')
ORDER BY id;
