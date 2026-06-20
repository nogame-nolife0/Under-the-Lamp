-- ============================================================
-- 试卷出题系统 - 基础种子数据
-- 分类树、枚举参考数据（可按实际学科扩展）
-- ============================================================

USE `paper-generator-system`;

-- ------------------------------------------------------------
-- 学科
-- ------------------------------------------------------------
INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`) VALUES
('数学',   'SUBJECT', 0, 1),
('物理',   'SUBJECT', 0, 2),
('化学',   'SUBJECT', 0, 3),
('语文',   'SUBJECT', 0, 4),
('英语',   'SUBJECT', 0, 5);

-- ------------------------------------------------------------
-- 年级（以初中为例，可按需修改）
-- ------------------------------------------------------------
INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`) VALUES
('初一', 'GRADE', 0, 1),
('初二', 'GRADE', 0, 2),
('初三', 'GRADE', 0, 3),
('高一', 'GRADE', 0, 4),
('高二', 'GRADE', 0, 5),
('高三', 'GRADE', 0, 6);

-- ------------------------------------------------------------
-- 数学章节示例（parent_id 需对应上面数学学科的 id，此处用子查询）
-- ------------------------------------------------------------
INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`)
SELECT '一元一次方程', 'CHAPTER', id, 1 FROM `category` WHERE `name` = '数学' AND `type` = 'SUBJECT' LIMIT 1;

INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`)
SELECT '二次函数', 'CHAPTER', id, 2 FROM `category` WHERE `name` = '数学' AND `type` = 'SUBJECT' LIMIT 1;

INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`)
SELECT '三角函数', 'CHAPTER', id, 3 FROM `category` WHERE `name` = '数学' AND `type` = 'SUBJECT' LIMIT 1;

INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`)
SELECT '导数与应用', 'CHAPTER', id, 4 FROM `category` WHERE `name` = '数学' AND `type` = 'SUBJECT' LIMIT 1;

-- ------------------------------------------------------------
-- 通用标签
-- ------------------------------------------------------------
INSERT INTO `category` (`name`, `type`, `parent_id`, `sort_order`) VALUES
('基础题',   'TAG', 0, 1),
('中等题',   'TAG', 0, 2),
('压轴题',   'TAG', 0, 3),
('易错题',   'TAG', 0, 4),
('期末复习', 'TAG', 0, 5);
