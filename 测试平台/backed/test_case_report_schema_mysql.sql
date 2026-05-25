-- MySQL schema for persisting API test case results (time-range filter only)
-- Run with: mysql -u<user> -p<password> <database> < test_case_report_schema_mysql.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;


