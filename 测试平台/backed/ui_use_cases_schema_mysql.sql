-- MySQL schema for UI use cases table
-- Run with: mysql -u<user> -p<password> <database> < ui_use_cases_schema_mysql.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 删除旧表（如存在）
DROP TABLE IF EXISTS `ui_use_cases`;

-- 建表：UI测试用例
CREATE TABLE `ui_use_cases` (
  `id` VARCHAR(36) NOT NULL COMMENT '用例ID (UUID)',
  `project_id` VARCHAR(36) NOT NULL COMMENT '所属项目ID',
  `name` VARCHAR(100) NOT NULL COMMENT '用例名称',
  `description` TEXT NULL COMMENT '用例描述',
  `url` VARCHAR(255) NOT NULL COMMENT '目标URL',
  `browser` VARCHAR(20) NOT NULL DEFAULT 'chrome' COMMENT '浏览器',
  `viewport` VARCHAR(20) NOT NULL DEFAULT '1920x1080' COMMENT '窗口大小',
  `headless` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否无头模式',
  `timeout` INT NOT NULL DEFAULT 30 COMMENT '超时时间 (秒)',
  `steps` JSON NOT NULL DEFAULT '[]' COMMENT '测试步骤 (JSON数组)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI测试用例表';

SET FOREIGN_KEY_CHECKS = 1;

