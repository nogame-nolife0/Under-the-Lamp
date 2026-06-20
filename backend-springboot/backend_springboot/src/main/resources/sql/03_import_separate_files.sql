-- 试卷与答案分文件导入：为 import_batch 增加答案文件字段
USE `paper-generator-system`;

ALTER TABLE `import_batch`
    ADD COLUMN `answer_file_name` VARCHAR(255) DEFAULT NULL COMMENT '答案卷原始文件名' AFTER `file_path`,
    ADD COLUMN `answer_file_path` VARCHAR(500) DEFAULT NULL COMMENT '答案卷服务器存储路径' AFTER `answer_file_name`,
    ADD COLUMN `import_mode` VARCHAR(20) NOT NULL DEFAULT 'COMBINED' COMMENT '导入模式：COMBINED/SEPARATE/STEM_ONLY' AFTER `grade`;
