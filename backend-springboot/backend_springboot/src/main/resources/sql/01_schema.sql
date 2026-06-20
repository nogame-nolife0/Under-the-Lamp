-- ============================================================
-- 试卷出题系统 - 表结构 DDL
-- MySQL 8.0+ | InnoDB | utf8mb4
-- ============================================================

USE `paper-generator-system`;

-- ------------------------------------------------------------
-- 1. 分类表（学科 / 年级 / 章节 / 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100)    NOT NULL                COMMENT '分类名称',
    `type`          VARCHAR(20)     NOT NULL                COMMENT '分类类型：SUBJECT/GRADE/CHAPTER/TAG',
    `parent_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父级ID，0表示根节点',
    `sort_order`    INT             NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_type_parent` (`type`, `parent_id`),
    KEY `idx_category_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分类表';

-- ------------------------------------------------------------
-- 2. 导入批次表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `import_batch` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_uuid`        VARCHAR(36)     NOT NULL                COMMENT '批次UUID，对外暴露',
    `file_name`         VARCHAR(255)    NOT NULL                COMMENT '原始文件名',
    `file_path`         VARCHAR(500)    NOT NULL                COMMENT '试卷（题干）服务器存储路径',
    `answer_file_name`  VARCHAR(255)    DEFAULT NULL            COMMENT '答案卷原始文件名',
    `answer_file_path`  VARCHAR(500)    DEFAULT NULL            COMMENT '答案卷服务器存储路径',
    `file_hash`         VARCHAR(64)     DEFAULT NULL            COMMENT '文件SHA256，用于去重',
    `file_size`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '文件大小（字节）',
    `subject`           VARCHAR(50)     DEFAULT NULL            COMMENT '学科（导入时指定）',
    `grade`             VARCHAR(50)     DEFAULT NULL            COMMENT '年级（导入时指定，可选）',
    `import_mode`       VARCHAR(20)     NOT NULL DEFAULT 'COMBINED' COMMENT '导入模式：COMBINED/SEPARATE/STEM_ONLY',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'PARSING' COMMENT '状态：PARSING/DRAFT/CONFIRMING/CONFIRMED/FAILED/PARTIAL',
    `total_count`       INT             NOT NULL DEFAULT 0      COMMENT '解析出的候选题总数',
    `accepted_count`    INT             NOT NULL DEFAULT 0      COMMENT '已接受题目数',
    `rejected_count`    INT             NOT NULL DEFAULT 0      COMMENT '已拒绝题目数',
    `needs_review_count` INT            NOT NULL DEFAULT 0      COMMENT '待人工确认题目数',
    `error_message`     TEXT            DEFAULT NULL            COMMENT '失败原因',
    `created_by`        VARCHAR(64)     DEFAULT NULL            COMMENT '出题人用户名',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_import_batch_uuid` (`batch_uuid`),
    KEY `idx_import_batch_status` (`status`),
    KEY `idx_import_batch_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Word导入批次表';

-- ------------------------------------------------------------
-- 3. 导入草稿表（Agent解析候选题，确认前不入正式题库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `import_item` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `batch_id`          BIGINT UNSIGNED NOT NULL                COMMENT '所属导入批次ID',
    `seq_no`            INT             NOT NULL                COMMENT '题目序号（批次内）',
    `stem_raw`          MEDIUMTEXT      DEFAULT NULL            COMMENT 'Agent原始题干文本',
    `answer_raw`        MEDIUMTEXT      DEFAULT NULL            COMMENT 'Agent原始答案文本',
    `stem_html`         MEDIUMTEXT      DEFAULT NULL            COMMENT '确认后题干（富文本/HTML）',
    `answer_html`       MEDIUMTEXT      DEFAULT NULL            COMMENT '确认后答案（富文本/HTML）',
    `analysis_html`     MEDIUMTEXT      DEFAULT NULL            COMMENT '确认后解析（富文本/HTML）',
    `options_json`      JSON            DEFAULT NULL            COMMENT '选项JSON，如 ["A.xx","B.xx"]',
    `question_type`     VARCHAR(30)     NOT NULL DEFAULT 'UNKNOWN' COMMENT '题型：SINGLE_CHOICE/MULTI_CHOICE/TRUE_FALSE/FILL_BLANK/SHORT_ANSWER/CALCULATION/ESSAY/UNKNOWN',
    `difficulty`        VARCHAR(20)     DEFAULT NULL            COMMENT '难度：EASY/MEDIUM/HARD',
    `subject`           VARCHAR(50)     DEFAULT NULL            COMMENT '学科',
    `grade`             VARCHAR(50)     DEFAULT NULL            COMMENT '年级',
    `chapter`           VARCHAR(100)    DEFAULT NULL            COMMENT '章节',
    `knowledge_points`  JSON            DEFAULT NULL            COMMENT '知识点列表JSON',
    `confidence_score`  DECIMAL(5,4)    DEFAULT NULL            COMMENT 'Agent置信度 0~1',
    `agent_meta`        JSON            DEFAULT NULL            COMMENT 'Agent解析元数据',
    `warnings`          JSON            DEFAULT NULL            COMMENT '警告信息列表',
    `images_json`       JSON            DEFAULT NULL            COMMENT '图片占位信息JSON',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ACCEPTED/REJECTED/EDITED',
    `question_id`       BIGINT UNSIGNED DEFAULT NULL            COMMENT '确认入库后关联的正式题目ID',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_import_item_batch_seq` (`batch_id`, `seq_no`),
    KEY `idx_import_item_batch_status` (`batch_id`, `status`),
    KEY `idx_import_item_confidence` (`confidence_score`),
    KEY `idx_import_item_question_id` (`question_id`),
    CONSTRAINT `fk_import_item_batch` FOREIGN KEY (`batch_id`) REFERENCES `import_batch` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导入草稿题目表';

-- ------------------------------------------------------------
-- 4. 正式题目表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `question` (
    `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stem`                  MEDIUMTEXT      NOT NULL                COMMENT '题干（富文本/HTML）',
    `options_json`          JSON            DEFAULT NULL            COMMENT '选项JSON',
    `answer`                MEDIUMTEXT      NOT NULL                COMMENT '标准答案',
    `analysis`              MEDIUMTEXT      DEFAULT NULL            COMMENT '解析',
    `question_type`         VARCHAR(30)     NOT NULL DEFAULT 'UNKNOWN' COMMENT '题型',
    `difficulty`            VARCHAR(20)     DEFAULT NULL            COMMENT '难度：EASY/MEDIUM/HARD',
    `subject`               VARCHAR(50)     DEFAULT NULL            COMMENT '学科',
    `grade`                 VARCHAR(50)     DEFAULT NULL            COMMENT '年级',
    `chapter`               VARCHAR(100)    DEFAULT NULL            COMMENT '章节',
    `knowledge_points`      JSON            DEFAULT NULL            COMMENT '知识点列表JSON',
    `score_default`         DECIMAL(6,2)    NOT NULL DEFAULT 0.00   COMMENT '默认分值',
    `source_batch_id`       BIGINT UNSIGNED DEFAULT NULL            COMMENT '来源导入批次ID',
    `source_item_id`        BIGINT UNSIGNED DEFAULT NULL            COMMENT '来源导入草稿ID',
    `confidence_snapshot`   DECIMAL(5,4)    DEFAULT NULL            COMMENT '入库时置信度快照',
    `images_json`           JSON            DEFAULT NULL            COMMENT '图片信息JSON',
    `embed_status`          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '向量同步状态：PENDING/SYNCED/FAILED',
    `embed_synced_at`       DATETIME        DEFAULT NULL            COMMENT '向量最近同步时间',
    `status`                VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/ARCHIVED',
    `version`               INT             NOT NULL DEFAULT 1      COMMENT '版本号',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_question_type` (`question_type`),
    KEY `idx_question_subject_grade` (`subject`, `grade`),
    KEY `idx_question_chapter` (`chapter`),
    KEY `idx_question_difficulty` (`difficulty`),
    KEY `idx_question_embed_status` (`embed_status`),
    KEY `idx_question_status_created` (`status`, `created_at`),
    KEY `idx_question_source_batch` (`source_batch_id`),
    KEY `idx_question_source_item` (`source_item_id`),
    CONSTRAINT `fk_question_source_batch` FOREIGN KEY (`source_batch_id`) REFERENCES `import_batch` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正式题目表';

-- ------------------------------------------------------------
-- 5. 题目-分类关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `question_category` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `question_id`   BIGINT UNSIGNED NOT NULL                COMMENT '题目ID',
    `category_id`   BIGINT UNSIGNED NOT NULL                COMMENT '分类ID',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_category` (`question_id`, `category_id`),
    KEY `idx_question_category_category` (`category_id`),
    CONSTRAINT `fk_qc_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_qc_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目分类关联表';

-- ------------------------------------------------------------
-- 6. 试卷表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `paper` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`             VARCHAR(200)    NOT NULL                COMMENT '试卷名称',
    `paper_type`        VARCHAR(20)     NOT NULL DEFAULT 'EXAM' COMMENT '类型：HOMEWORK/EXAM/EXAMPLE',
    `total_score`       DECIMAL(8,2)    NOT NULL DEFAULT 0.00   COMMENT '总分',
    `duration_minutes`  INT             DEFAULT NULL            COMMENT '考试时长（分钟）',
    `subject`           VARCHAR(50)     DEFAULT NULL            COMMENT '学科',
    `grade`             VARCHAR(50)     DEFAULT NULL            COMMENT '年级',
    `description`       VARCHAR(500)    DEFAULT NULL            COMMENT '试卷说明',
    `compose_mode`      VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT '组卷方式：MANUAL/SMART',
    `compose_condition` JSON            DEFAULT NULL            COMMENT '智能组卷条件JSON',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/COMPLETED',
    `created_by`        VARCHAR(64)     DEFAULT NULL            COMMENT '出题人用户名',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_paper_type_status` (`paper_type`, `status`),
    KEY `idx_paper_subject_grade` (`subject`, `grade`),
    KEY `idx_paper_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

-- ------------------------------------------------------------
-- 7. 试卷-题目关联表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `paper_question` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `paper_id`      BIGINT UNSIGNED NOT NULL                COMMENT '试卷ID',
    `question_id`   BIGINT UNSIGNED NOT NULL                COMMENT '题目ID',
    `sort_order`    INT             NOT NULL DEFAULT 0      COMMENT '题目顺序',
    `score`         DECIMAL(6,2)    NOT NULL DEFAULT 0.00   COMMENT '该题在本卷中的分值',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
    KEY `idx_paper_question_paper_sort` (`paper_id`, `sort_order`),
    KEY `idx_paper_question_question` (`question_id`),
    CONSTRAINT `fk_pq_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_pq_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目关联表';

-- ------------------------------------------------------------
-- 8. 导出记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `export_record` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `paper_id`      BIGINT UNSIGNED NOT NULL                COMMENT '试卷ID',
    `export_type`   VARCHAR(20)     NOT NULL                COMMENT '导出类型：STUDENT/TEACHER',
    `file_format`   VARCHAR(10)     NOT NULL DEFAULT 'DOCX' COMMENT '文件格式：DOCX/PDF',
    `file_name`     VARCHAR(255)    NOT NULL                COMMENT '文件名',
    `file_path`     VARCHAR(500)    NOT NULL                COMMENT '文件存储路径',
    `file_size`     BIGINT UNSIGNED DEFAULT NULL            COMMENT '文件大小（字节）',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'GENERATING' COMMENT '状态：GENERATING/SUCCESS/FAILED',
    `error_message` TEXT            DEFAULT NULL            COMMENT '失败原因',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_export_paper_id` (`paper_id`),
    KEY `idx_export_status_created` (`status`, `created_at`),
    CONSTRAINT `fk_export_paper` FOREIGN KEY (`paper_id`) REFERENCES `paper` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷导出记录表';

-- ------------------------------------------------------------
-- 补充外键：import_item 确认后关联 question
-- ------------------------------------------------------------
ALTER TABLE `import_item`
    ADD CONSTRAINT `fk_import_item_question`
    FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE SET NULL;
