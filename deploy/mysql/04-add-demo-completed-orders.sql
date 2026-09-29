-- 为本机开发库 demo_customer 追加 5 笔已完成订单，不修改已有订单或售后记录。
-- 在 IDEA / Navicat 中执行整个文件即可；重复执行跳过，不刷新签收时间。
USE aftersales_agent;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DELIMITER $$
DROP PROCEDURE IF EXISTS add_demo_completed_orders_v1$$
CREATE PROCEDURE add_demo_completed_orders_v1()
seed: BEGIN
    DECLARE seed_lock INT DEFAULT 0;
    DECLARE customer_id BIGINT;
    DECLARE order_id_value BIGINT;
    DECLARE shipment_id_value BIGINT;
    DECLARE sequence_value INT DEFAULT 1;
    DECLARE number_value VARCHAR(64);
    DECLARE product_value VARCHAR(200);
    DECLARE specification_value VARCHAR(255);
    DECLARE quantity_value INT;
    DECLARE amount_value DECIMAL(12,2);
    DECLARE now_value DATETIME(3);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF seed_lock = 1 THEN
            DO RELEASE_LOCK(CONCAT(DATABASE(), ':demo-completed-orders-v1'));
        END IF;
        RESIGNAL;
    END;

    SELECT GET_LOCK(CONCAT(DATABASE(), ':demo-completed-orders-v1'), 5) INTO seed_lock;
    IF seed_lock IS NULL OR seed_lock <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot acquire demo order seed lock';
    END IF;
    START TRANSACTION;
    IF EXISTS (SELECT 1 FROM project_metadata WHERE metadata_key = 'demo_completed_orders_v1') THEN
        COMMIT;
        DO RELEASE_LOCK(CONCAT(DATABASE(), ':demo-completed-orders-v1'));
        SELECT 'Already imported; existing orders and signed dates preserved' AS result;
        LEAVE seed;
    END IF;

    SELECT id INTO customer_id FROM app_user
        WHERE username = 'demo_customer' AND role = 'CUSTOMER' AND enabled = TRUE FOR UPDATE;
    IF customer_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Enabled demo_customer account is required';
    END IF;
    IF EXISTS (SELECT 1 FROM trade_order WHERE order_number IN ('DEMO-2001','DEMO-2002','DEMO-2003','DEMO-2004','DEMO-2005')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Reserved demo order numbers already exist; no data changed';
    END IF;

    SET now_value = UTC_TIMESTAMP(3);
    WHILE sequence_value <= 5 DO
        SET number_value = CONCAT('DEMO-', 2000 + sequence_value);
        SET product_value = CASE sequence_value
            WHEN 1 THEN '机械键盘' WHEN 2 THEN '蓝牙耳机' WHEN 3 THEN '保温杯'
            WHEN 4 THEN '桌面音箱' ELSE 'USB-C 数据线' END;
        SET specification_value = CASE sequence_value
            WHEN 1 THEN '白色 / 87键' WHEN 2 THEN '黑色 / 入耳式' WHEN 3 THEN '银色 / 500ml'
            WHEN 4 THEN '黑色 / 双声道' ELSE '白色 / 1米' END;
        SET quantity_value = CASE sequence_value WHEN 3 THEN 2 WHEN 5 THEN 3 ELSE 1 END;
        -- paid_amount 是整行商品实付金额，不是单价。
        SET amount_value = CASE sequence_value
            WHEN 1 THEN 299.00 WHEN 2 THEN 199.00 WHEN 3 THEN 178.00
            WHEN 4 THEN 159.00 ELSE 59.90 END;
        INSERT INTO trade_order(user_id,order_number,status,paid_amount,created_at,paid_at,signed_at)
        VALUES(customer_id,number_value,'COMPLETED',amount_value,
            now_value - INTERVAL 4 DAY,now_value - INTERVAL 4 DAY + INTERVAL 5 MINUTE,now_value - INTERVAL 1 DAY);
        SET order_id_value = LAST_INSERT_ID();
        INSERT INTO order_item(order_id,sku_id,product_name,specification,quantity,paid_amount)
        VALUES(order_id_value,600 + sequence_value,product_value,specification_value,quantity_value,amount_value);
        INSERT INTO order_shipment(order_id,carrier,tracking_number,status,shipped_at,delivered_at)
        VALUES(order_id_value,'模拟物流',CONCAT('MOCK-',number_value),'DELIVERED',now_value - INTERVAL 3 DAY,now_value - INTERVAL 1 DAY);
        SET shipment_id_value = LAST_INSERT_ID();
        INSERT INTO shipment_event(shipment_id,occurred_at,description) VALUES
            (shipment_id_value,now_value - INTERVAL 3 DAY,'模拟轨迹：包裹已揽收'),
            (shipment_id_value,now_value - INTERVAL 1 DAY,'模拟轨迹：包裹已签收');
        SET sequence_value = sequence_value + 1;
    END WHILE;
    INSERT INTO project_metadata(metadata_key,metadata_value) VALUES('demo_completed_orders_v1','1');
    COMMIT;
    DO RELEASE_LOCK(CONCAT(DATABASE(), ':demo-completed-orders-v1'));
    SELECT 'Added 5 completed orders, 5 items, 5 shipments and 10 shipment events' AS result;
END$$
DELIMITER ;
CALL add_demo_completed_orders_v1();
DROP PROCEDURE add_demo_completed_orders_v1;

SELECT u.username,o.order_number,o.status,i.product_name,i.quantity,i.paid_amount,o.signed_at
FROM trade_order o JOIN app_user u ON u.id=o.user_id JOIN order_item i ON i.order_id=o.id
WHERE u.username='demo_customer' AND o.order_number IN ('DEMO-2001','DEMO-2002','DEMO-2003','DEMO-2004','DEMO-2005')
ORDER BY o.order_number;
