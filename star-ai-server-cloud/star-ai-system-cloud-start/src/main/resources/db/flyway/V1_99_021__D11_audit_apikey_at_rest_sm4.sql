ALTER TABLE audit_trail_apikey ADD COLUMN apikey_cipher VARBINARY(512) NULL COMMENT 'D11 SM4 at rest cipher of apikey plain';
-- D11 placeholder migration: IF apikey (VARCHAR plaintext column) EXISTS, migrate it as:
-- UPDATE audit_trail_apikey SET apikey_cipher = SM4_ENCRYPT(apikey, UNHEX(SHA2(@@JEECG_DB_AES_KEY_HEX, 256))) WHERE apikey IS NOT NULL;
UPDATE audit_trail_apikey SET apikey_cipher = NULL WHERE 1=0;
ALTER TABLE audit_trail_apikey DROP COLUMN IF EXISTS apikey;
CREATE INDEX idx_audit_apikey_ref ON audit_trail_apikey(apikey_ref_id);
