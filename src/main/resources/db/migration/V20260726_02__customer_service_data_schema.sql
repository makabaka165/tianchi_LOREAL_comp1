-- DATA-001: service data context schema (cs_data_*).
-- Owns imported source facts only. Rules enforced here:
--   * every table carries tenant_id/workspace_id/created_at; mutable aggregates add
--     updated_at + version;
--   * business numbers (order no, case no, logistics no) are VARCHAR end to end;
--   * no evaluation label columns (scene_major/scene_minor/target*) may ever appear;
--   * unique keys embed the scope directly or inherit it through a parent unique key;
--   * order snapshots and service cases are append-only versioned facts (no overwrite);
--   * conversation-order/case relations live in cs_data_source_link (one-to-many safe).

CREATE TABLE IF NOT EXISTS cs_data_import_batch (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  source_sha256 CHAR(64) NOT NULL,
  parser_version VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  preview_counts_json TEXT NULL,
  commit_counts_json TEXT NULL,
  warning_count INT NOT NULL DEFAULT 0,
  blocking_error_count INT NOT NULL DEFAULT 0,
  staging_expires_at TIMESTAMP(3) NULL DEFAULT NULL,
  confirmed_at TIMESTAMP(3) NULL DEFAULT NULL,
  confirmed_by VARCHAR(64) NULL,
  created_by VARCHAR(64) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  KEY idx_cs_import_batch_scope_hash (tenant_id, workspace_id, source_sha256, parser_version, status),
  KEY idx_cs_import_batch_scope_created (tenant_id, workspace_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_import_staging (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  batch_id VARCHAR(64) NOT NULL,
  record_type VARCHAR(32) NOT NULL,
  sheet_name VARCHAR(128) NOT NULL,
  row_no INT NOT NULL,
  source_key VARCHAR(191) NOT NULL,
  payload_json MEDIUMTEXT NOT NULL,
  expires_at TIMESTAMP(3) NULL DEFAULT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cs_staging_batch_type_key (batch_id, record_type, source_key),
  KEY idx_cs_staging_scope_batch (tenant_id, workspace_id, batch_id, record_type),
  KEY idx_cs_staging_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_import_error (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  batch_id VARCHAR(64) NOT NULL,
  sheet_name VARCHAR(128) NOT NULL,
  row_no INT NOT NULL,
  field_name VARCHAR(128) NULL,
  error_code VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  masked_raw_value VARCHAR(255) NULL,
  message VARCHAR(500) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_cs_import_error_batch (tenant_id, workspace_id, batch_id, severity, sheet_name, row_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_consumer (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  merge_policy VARCHAR(32) NOT NULL DEFAULT 'LIMITED_SOURCE_SCOPE',
  created_by VARCHAR(64) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  KEY idx_cs_consumer_scope (tenant_id, workspace_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_consumer_alias (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  consumer_id VARCHAR(64) NOT NULL,
  source_system VARCHAR(64) NOT NULL,
  source_scope VARCHAR(128) NOT NULL,
  display_alias VARCHAR(255) NOT NULL,
  normalized_alias_hash CHAR(64) NOT NULL,
  merge_confidence VARCHAR(16) NOT NULL DEFAULT 'LIMITED',
  provenance_json TEXT NULL,
  import_batch_id VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cs_alias_identity (tenant_id, workspace_id, source_system, source_scope, normalized_alias_hash),
  KEY idx_cs_alias_consumer (consumer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_conversation (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  source_system VARCHAR(64) NOT NULL,
  source_conversation_id VARCHAR(128) NOT NULL,
  consumer_id VARCHAR(64) NOT NULL,
  channel VARCHAR(64) NULL,
  status VARCHAR(32) NULL,
  started_at TIMESTAMP(3) NULL DEFAULT NULL,
  ended_at TIMESTAMP(3) NULL DEFAULT NULL,
  message_count INT NOT NULL DEFAULT 0,
  first_message_at TIMESTAMP(3) NULL DEFAULT NULL,
  last_message_at TIMESTAMP(3) NULL DEFAULT NULL,
  content_hash CHAR(64) NULL,
  import_batch_id VARCHAR(64) NULL,
  created_by VARCHAR(64) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_cs_conversation_source (tenant_id, workspace_id, source_system, source_conversation_id),
  KEY idx_cs_conversation_consumer (tenant_id, workspace_id, consumer_id, last_message_at),
  KEY idx_cs_conversation_status (tenant_id, workspace_id, status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_message (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  source_message_key VARCHAR(191) NOT NULL,
  sender_role VARCHAR(32) NOT NULL,
  sender_alias VARCHAR(255) NULL,
  content MEDIUMTEXT NULL,
  content_type VARCHAR(32) NOT NULL DEFAULT 'TEXT',
  media_path VARCHAR(500) NULL,
  media_status VARCHAR(32) NULL,
  sent_at TIMESTAMP(3) NULL DEFAULT NULL,
  source_sequence INT NOT NULL,
  import_batch_id VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cs_message_source (conversation_id, source_message_key),
  KEY idx_cs_message_timeline (conversation_id, source_sequence),
  KEY idx_cs_message_scope_time (tenant_id, workspace_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_order_snapshot (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  snapshot_seq INT NOT NULL DEFAULT 1,
  source_system VARCHAR(64) NOT NULL,
  source_key VARCHAR(191) NULL,
  order_status VARCHAR(64) NULL,
  product_name VARCHAR(500) NULL,
  sku VARCHAR(255) NULL,
  quantity INT NULL,
  amount DECIMAL(12, 2) NULL,
  currency VARCHAR(8) NULL,
  ordered_at TIMESTAMP(3) NULL DEFAULT NULL,
  paid_at TIMESTAMP(3) NULL DEFAULT NULL,
  shipped_at TIMESTAMP(3) NULL DEFAULT NULL,
  received_at TIMESTAMP(3) NULL DEFAULT NULL,
  logistics_no VARCHAR(64) NULL,
  logistics_company VARCHAR(128) NULL,
  detail_schema_version INT NOT NULL DEFAULT 1,
  detail_json MEDIUMTEXT NULL,
  content_hash CHAR(64) NOT NULL,
  import_batch_id VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cs_order_content (tenant_id, workspace_id, order_no, content_hash),
  KEY idx_cs_order_no (tenant_id, workspace_id, order_no, snapshot_seq),
  KEY idx_cs_order_batch (import_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_service_case (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  case_no VARCHAR(64) NOT NULL,
  case_seq INT NOT NULL DEFAULT 1,
  source_system VARCHAR(64) NOT NULL,
  source_key VARCHAR(191) NULL,
  case_type VARCHAR(64) NULL,
  case_status VARCHAR(64) NULL,
  priority VARCHAR(32) NULL,
  order_no VARCHAR(64) NULL,
  opened_at TIMESTAMP(3) NULL DEFAULT NULL,
  closed_at TIMESTAMP(3) NULL DEFAULT NULL,
  description MEDIUMTEXT NULL,
  resolution MEDIUMTEXT NULL,
  detail_schema_version INT NOT NULL DEFAULT 1,
  detail_json MEDIUMTEXT NULL,
  content_hash CHAR(64) NOT NULL,
  import_batch_id VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cs_case_content (tenant_id, workspace_id, case_no, content_hash),
  KEY idx_cs_case_no (tenant_id, workspace_id, case_no, case_seq),
  KEY idx_cs_case_open (tenant_id, workspace_id, case_status, opened_at),
  KEY idx_cs_case_batch (import_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cs_data_source_link (
  id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  workspace_id VARCHAR(64) NOT NULL,
  link_type VARCHAR(32) NOT NULL,
  from_id VARCHAR(64) NOT NULL,
  to_ref VARCHAR(128) NOT NULL,
  confidence VARCHAR(16) NULL,
  provenance_json TEXT NULL,
  import_batch_id VARCHAR(64) NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cs_link (tenant_id, workspace_id, link_type, from_id, to_ref),
  KEY idx_cs_link_to (tenant_id, workspace_id, link_type, to_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
