-- 完整的测试平台数据库结构
-- 运行命令: mysql -u<user> -p<password> < full_database_schema.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ======================================
-- 测试用例执行结果表
-- ======================================
-- 删除旧表（如存在）
DROP TABLE IF EXISTS `test_case_report`;

-- 建表：测试用例执行结果
CREATE TABLE `test_case_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `case_id` INT NULL COMMENT '用例ID，对应用例库主键',
  `case_name` VARCHAR(255) NULL COMMENT '用例名称',
  `module_name` VARCHAR(255) NULL COMMENT '模块名称（来自@ApiTest.module）',
  `description` VARCHAR(512) NULL COMMENT '用例描述',
  `api_url` VARCHAR(1024) NULL COMMENT '接口地址',
  `request_method` VARCHAR(16) NULL COMMENT 'HTTP方法',
  `request_headers` TEXT NULL COMMENT '请求头（JSON字符串）',
  `request_body` MEDIUMTEXT NULL COMMENT '请求体（JSON字符串或文本）',
  `response_status` INT NULL COMMENT '响应状态码',
  `response_headers` MEDIUMTEXT NULL COMMENT '响应头（JSON字符串）',
  `response_body` MEDIUMTEXT NULL COMMENT '响应体（JSON或文本）',
  `duration` BIGINT NULL COMMENT '响应耗时（毫秒）',
  `assert_detail` MEDIUMTEXT NULL COMMENT '断言结果详情（JSON字符串）',
  `status` VARCHAR(16) NULL COMMENT '执行状态：passed/failed/broken',
  `allure_result_json` MEDIUMTEXT NULL COMMENT 'Allure结果JSON（包含attachments元数据）',
  `start_time` DATETIME NULL COMMENT '用例开始时间',
  `end_time` DATETIME NULL COMMENT '用例结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_case_id` (`case_id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API测试用例执行结果';

-- ======================================
-- UI测试用例表
-- ======================================
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

-- 执行完成提示
-- 数据库已创建，表结构已初始化
-- 数据库名称：test_platform
