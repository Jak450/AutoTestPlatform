SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 项目表
DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- UI项目表
DROP TABLE IF EXISTS `uiproject`;
CREATE TABLE `uiproject` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NOT NULL,
  `description` TEXT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- API测试用例表
DROP TABLE IF EXISTS `use_case`;
CREATE TABLE `use_case` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `pid` INT NOT NULL COMMENT '项目ID',
  `name` VARCHAR(255) NOT NULL COMMENT '用例名称',
  `url` VARCHAR(1024) NOT NULL COMMENT '接口地址',
  `method` VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
  `header` TEXT NULL COMMENT '请求头JSON',
  `param` TEXT NULL COMMENT '请求参数JSON',
  `assert_str` TEXT NULL COMMENT '断言JSON',
  `description` VARCHAR(512) NULL COMMENT '用例描述',
  PRIMARY KEY (`id`),
  KEY `idx_pid` (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 测试执行报告表
DROP TABLE IF EXISTS `test_case_report`;
CREATE TABLE `test_case_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `case_id` INT NULL,
  `case_name` VARCHAR(255) NULL,
  `module_name` VARCHAR(255) NULL,
  `description` VARCHAR(512) NULL,
  `api_url` VARCHAR(1024) NULL,
  `request_method` VARCHAR(16) NULL,
  `request_headers` TEXT NULL,
  `request_body` MEDIUMTEXT NULL,
  `response_status` INT NULL,
  `response_headers` MEDIUMTEXT NULL,
  `response_body` MEDIUMTEXT NULL,
  `duration` BIGINT NULL,
  `assert_detail` MEDIUMTEXT NULL,
  `status` VARCHAR(16) NULL,
  `allure_result_json` MEDIUMTEXT NULL,
  `start_time` DATETIME NULL,
  `end_time` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_case_id` (`case_id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- UI测试用例表
DROP TABLE IF EXISTS `ui_use_cases`;
CREATE TABLE `ui_use_cases` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `project_id` VARCHAR(36) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT NULL,
  `url` VARCHAR(255) NOT NULL,
  `browser` VARCHAR(20) NOT NULL DEFAULT 'chrome',
  `viewport` VARCHAR(20) NOT NULL DEFAULT '1920x1080',
  `headless` BOOLEAN NOT NULL DEFAULT TRUE,
  `timeout` INT NOT NULL DEFAULT 30,
  `steps` JSON NOT NULL DEFAULT ('[]'),
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
