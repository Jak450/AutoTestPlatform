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

-- =====================================================================
-- Agent 模块（可交互测试 Agent）schema v0.1
-- 来源: docs/agent-design/sql/agent_schema.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `agent_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `display_name` VARCHAR(64) NOT NULL DEFAULT '',
  `role` VARCHAR(16) NOT NULL DEFAULT 'user',
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `title` VARCHAR(128) NOT NULL DEFAULT '',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active',
  `active_tool_names` JSON NULL,
  `context_summary` MEDIUMTEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_conversation_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `role` VARCHAR(16) NOT NULL,
  `type` VARCHAR(24) NOT NULL,
  `content` MEDIUMTEXT NULL,
  `tool_meta` JSON NULL,
  `seq` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_message_conv_seq` (`conversation_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_attachment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `mime_type` VARCHAR(128) NOT NULL DEFAULT '',
  `size_bytes` BIGINT NOT NULL DEFAULT 0,
  `storage_path` VARCHAR(512) NOT NULL,
  `parse_status` VARCHAR(16) NOT NULL DEFAULT 'pending',
  `parse_result_ref` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_attachment_conv` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_confirmation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `message_id` BIGINT UNSIGNED NULL,
  `tool_name` VARCHAR(64) NOT NULL,
  `payload` JSON NULL,
  `payload_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'pending',
  `expires_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `responded_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_confirmation_conv_status` (`conversation_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_case_template` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(512) NOT NULL DEFAULT '',
  `case_shape` TEXT NOT NULL,
  `coverage_rules` TEXT NULL,
  `assert_rules` TEXT NULL,
  `examples` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_template_user_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_memory` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `scope` VARCHAR(16) NOT NULL DEFAULT 'user',
  `namespace` VARCHAR(32) NOT NULL DEFAULT 'preference',
  `mem_key` VARCHAR(128) NOT NULL,
  `content_md` TEXT NOT NULL,
  `tags` JSON NULL,
  `confidence` VARCHAR(8) NOT NULL DEFAULT 'medium',
  `confirmed` TINYINT(1) NOT NULL DEFAULT 0,
  `source_session_id` BIGINT UNSIGNED NULL,
  `version` INT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_memory_user_key` (`user_id`, `scope`, `namespace`, `mem_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_tool_registry` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tool_name` VARCHAR(64) NOT NULL,
  `label` VARCHAR(64) NOT NULL DEFAULT '',
  `description` TEXT NULL,
  `input_schema` JSON NULL,
  `category` VARCHAR(24) NOT NULL DEFAULT '',
  `permission` VARCHAR(24) NOT NULL DEFAULT 'read',
  `active_by_default` TINYINT(1) NOT NULL DEFAULT 1,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `version` VARCHAR(16) NOT NULL DEFAULT '1.0.0',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_name` (`tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_audit_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NULL,
  `conversation_id` BIGINT UNSIGNED NULL,
  `run_id` VARCHAR(64) NULL,
  `action` VARCHAR(64) NOT NULL,
  `detail` JSON NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_audit_user_time` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认管理员账号: admin / 密码由应用启动时初始化（BCrypt），勿在此写入明文

CREATE TABLE IF NOT EXISTS `agent_tool_execution` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` BIGINT UNSIGNED NOT NULL,
  `tool_call_id` VARCHAR(128) NULL,
  `tool_name` VARCHAR(64) NOT NULL,
  `payload_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'running',
  `result` TEXT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_exec_conv_tool_hash` (`conversation_id`, `tool_name`, `payload_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
