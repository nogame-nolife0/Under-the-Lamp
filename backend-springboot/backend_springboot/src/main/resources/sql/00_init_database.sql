-- ============================================================
-- 试卷出题系统 - 数据库初始化
-- MySQL 8.0+
-- 执行顺序：00 → 01 → 02
-- 说明：若已在 Navicat 等工具中创建总库，可只执行 USE 语句
-- ============================================================

CREATE DATABASE IF NOT EXISTS `paper-generator-system`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `paper-generator-system`;
