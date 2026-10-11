ALTER TABLE sys_user ADD COLUMN mfa_legacy_cutoff_at DATETIME NULL COMMENT 'D4 legacy AES MFA deadline after which old ciphers are rejected';
ALTER TABLE sys_user ADD COLUMN mfa_master_version INT NOT NULL DEFAULT 0 COMMENT 'D4 0=legacy AES; 1=MFA_MASTER_SECRET AES-GCM';
UPDATE sys_user SET mfa_legacy_cutoff_at = DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE mfa_legacy_cutoff_at IS NULL;
CREATE INDEX idx_sys_user_mfa_master_version ON sys_user(mfa_master_version);
