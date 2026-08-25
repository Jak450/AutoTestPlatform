-- 记忆层地基 v1（幂等，可重复执行）
CREATE TABLE IF NOT EXISTS memory_episode (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  source_type VARCHAR(32) NOT NULL,
  source_ref VARCHAR(128) NULL,
  content MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  archived_at DATETIME NULL,
  KEY idx_episode_ws_time (workspace_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS memory_fact (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  entity_id VARCHAR(64) NOT NULL,
  attribute VARCHAR(128) NOT NULL,
  fact_value VARCHAR(512) NOT NULL,
  valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  valid_to DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
  version INT NOT NULL DEFAULT 1,
  source_type VARCHAR(32) NULL,
  source_ref VARCHAR(128) NULL,
  confidence DECIMAL(3,2) NOT NULL DEFAULT 0.50,
  embedding_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fact_current (workspace_id, entity_id, attribute, valid_to),
  KEY idx_fact_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS memory_experience (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  workspace_id BIGINT NOT NULL DEFAULT 0,
  user_id BIGINT NOT NULL DEFAULT 0,
  task_type VARCHAR(64) NOT NULL,
  rule_text TEXT NOT NULL,
  evidence TEXT NULL,
  hits INT NOT NULL DEFAULT 0,
  last_used_at DATETIME NULL,
  confidence DECIMAL(3,2) NOT NULL DEFAULT 0.50,
  confirmed TINYINT NOT NULL DEFAULT 0,
  embedding_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_exp_task (workspace_id, task_type),
  KEY idx_exp_confirmed (workspace_id, confirmed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
