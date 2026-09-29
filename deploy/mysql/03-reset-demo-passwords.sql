-- 仅修改三个演示账号的密码为 123，不删除账号或业务数据。
-- 数据库存储 PBKDF2 摘要，不存储明文；在 IDEA 中运行整个文件即可。
USE aftersales_agent;
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE app_user
SET password_hash = '{pbkdf2}a3871b1ca1b54129ba0f17c2e3fe957dca5741fe4dbdbf3be0e87e3a6cfb331ae8e75f684c23f20d0b06a73081d24769'
WHERE (username IN ('demo_customer', 'demo_other') AND role = 'CUSTOMER')
   OR (username = 'demo_staff' AND role = 'STAFF');
COMMIT;
SELECT username, role FROM app_user WHERE username IN ('demo_customer', 'demo_other', 'demo_staff');
