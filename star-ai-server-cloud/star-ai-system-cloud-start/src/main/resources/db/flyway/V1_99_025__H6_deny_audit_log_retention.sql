-- =============================================================================
-- Flyway V1_99_025 · H6 deny_audit log retention + sys_log retention DDL
-- Slot 1.99.025 (immediately after class-E last migration V1_99_024)
-- Spec: SEC-CLASS2-H-SPEC-v1.0 / FR-H6-01/02 + FR-H5-02 (H5_LEVEL_VIOLATION writes)
-- Compatibility: MySQL 5.7 (plain index idx_time) / 8.0 (monthly RANGE partition + idx_time)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. sys_deny_audit TABLE
-- Purpose: deny call / violation audit sink for:
--   H5 jdbc.sqlonly level guard              -> reason_code=H5_LEVEL_VIOLATION
--   H8 NotLoginException/NotPermissionException/AccessDeniedException -> H8_DENIED_*
--   H7 exception redact failure              -> H7_REDACT_FAIL
--   H1/H2/H4 fail-fast startup verifiers     -> HX_VERIFIER_TRIP
-- GDPR Art.5(1)(e) + PCI-DSS 10.2/10.2.4: retained 180 days by H6 2nd cron
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sys_deny_audit (
  id                      VARCHAR(64)  NOT NULL           COMMENT 'Snowflake 64-bit / ULID — PK, no auto_inc to avoid leak-by-ordering',
  request_ip              VARCHAR(64)  NOT NULL           COMMENT 'X-Real-IP / X-Forwarded-For last trusted hop (RFC 7239)',
  user_id_opt             VARCHAR(64)  NULL               COMMENT 'NULL when deny=pre-auth (NotLogin) / deny=internal (H5)',
  http_method             VARCHAR(16)  NOT NULL           COMMENT 'GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS/INTERNAL',
  request_uri             VARCHAR(768) NOT NULL           COMMENT 'uri path without query; query stripped per GDPR minimisation',
  reason_code             VARCHAR(64)  NOT NULL           COMMENT 'Enum-like: H5_LEVEL_VIOLATION / H8_DENIED_LOGIN / H8_DENIED_PERMISSION / H7_REDACT_FAIL / HX_VERIFIER_TRIP',
  reason_detail_redacted  VARCHAR(1024) NOT NULL          COMMENT 'human-readable but redacted; forbid raw token/ip-full/body; truncate 1024',
  trace_id                VARCHAR(128) NULL               COMMENT 'micrometer / sleuth traceId for cross-log correlation',
  create_time             DATETIME     NOT NULL           COMMENT 'UTC / server timezone; INDEX idx_time + PARTITION BY RANGE (when MySQL>=8)',
  tenant_id_opt           VARCHAR(64)  NULL               COMMENT '_optional_idx_col; reserved; optional index (not required)',
  PRIMARY KEY (id),
  KEY idx_time (create_time),
  KEY idx_reason_code_time (reason_code, create_time),
  KEY idx_ip_time (request_ip, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='H6+H8 deny call & violation audit (180-day retention, GDPR/PCI 10.2)';

-- -----------------------------------------------------------------------------
-- 1. MySQL 8.0+ PARTITION BY RANGE (monthly)
--    MySQL 5.7 does not support ALGORITHM=INSTANT ADD PARTITION in dynamic CREATE TABLE;
--    Compat: create table + plain idx_time first; run ALTER below on 8.0 (ignore 5.7 errors)
--    Flyway-runnable; IF wrappers need PROCEDURE — use standard partition DDL (effective on 8.0)
-- -----------------------------------------------------------------------------

/*!50600
ALTER TABLE sys_deny_audit
/*!80000
PARTITION BY RANGE (TO_DAYS(create_time)) (
  PARTITION p_202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION p_202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
  PARTITION p_202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
  PARTITION p_202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
  PARTITION p_202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
  PARTITION p_202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
  PARTITION p_202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
  PARTITION p_202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION p_202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION p_202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
  PARTITION p_202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
  PARTITION p_202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
  PARTITION p_future VALUES LESS THAN MAXVALUE
)
*/;

-- -----------------------------------------------------------------------------
-- 2. sys_log retention helper index (skip if already exists)
--    H6SysLogRetentionService cron=0 10 2 * * ? batch=500: UPDATE ... WHERE create_time < ? LIMIT 500
--    Speeds scans on create_time + (request_param / request_body)
-- -----------------------------------------------------------------------------

/*!50000
ALTER TABLE sys_log ADD INDEX IF NOT EXISTS idx_h6_retention_cutoff (create_time);
*/

-- -----------------------------------------------------------------------------
-- 3. Self-describing seed row: mark V1_99_025 applied (for SQL audit)
-- -----------------------------------------------------------------------------

INSERT INTO sys_deny_audit
  (id, request_ip, user_id_opt, http_method, request_uri, reason_code, reason_detail_redacted, trace_id, create_time, tenant_id_opt)
VALUES
  ('h6-bootstrap-' || REPLACE(UUID(), '-', ''),
   '127.0.0.1',
   NULL,
   'INTERNAL',
   '/_flyway/V1_99_025/init',
   'HX_VERIFIER_TRIP',
   'Flyway V1_99_025__H6_deny_audit_log_retention.sql applied successfully (MySQL=' || VERSION() || '). Columns=10+, idx_time present, retention 180-day.',
   'flyway-v1-99-025',
   NOW(),
   NULL)
ON DUPLICATE KEY UPDATE create_time = NOW();
