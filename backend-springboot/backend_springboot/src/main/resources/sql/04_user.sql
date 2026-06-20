-- 系统用户表（支持多用户注册）
USE `paper-generator-system`;

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(50)     NOT NULL                COMMENT '用户名',
    `phone`         VARCHAR(20)     NOT NULL                COMMENT '手机号',
    `password_hash` VARCHAR(100)    NOT NULL                COMMENT '密码哈希',
    `password_plain` VARCHAR(100)   NOT NULL                COMMENT '明文密码（单用户简易找回，勿用于生产）',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    UNIQUE KEY `uk_sys_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';
