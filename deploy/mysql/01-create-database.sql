-- Run manually with an account allowed to create databases.
-- Use a dedicated development database; no existing tables are modified here.
CREATE DATABASE IF NOT EXISTS aftersales_agent
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Create a dedicated local development account using your database client.
-- Grant only access to aftersales_agent.* (including DDL for Flyway).
-- Do not place real account passwords in this file.
