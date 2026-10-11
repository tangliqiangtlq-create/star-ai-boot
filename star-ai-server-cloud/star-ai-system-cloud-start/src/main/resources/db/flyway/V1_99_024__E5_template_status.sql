-- __E5_FLYWAY__ V1_99_024: sys_message_template status column + 6 system default templates APPROVED
-- __E5_FLYWAY__ Date: 2026-09-27
-- __E5_FLYWAY__ Description: Add status enum column to sys_sms_template (SysMessageTemplate)
-- __E5_FLYWAY__ and set 6 pre-installed system templates to APPROVED to prevent
-- __E5_FLYWAY__ accidental lockout of OTP/account email delivery after deploy.

ALTER TABLE sys_sms_template
    ADD COLUMN status ENUM('DRAFT', 'APPROVAL', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'DRAFT'
    COMMENT 'E5 template render approval status: DRAFT editable, APPROVAL awaiting approver, APPROVED renderable, REJECTED blocked';

-- __E5_FLYWAY__ Approve 6 critical system templates by template_code (verification/account/ops)
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND template_code = 'verification_email_code';
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND template_code = 'account_created_welcome';
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND template_code = 'account_register_success';
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND template_code = 'account_locked_notice';
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND template_code = 'self_register_success';
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND template_code = 'ops_generic_notify';

-- __E5_FLYWAY__ For any remaining templates already shipped in prior seeds, default to APPROVED
-- __E5_FLYWAY__ only when use_status = 1 (active) to avoid promoting unknown DRAFT templates.
UPDATE sys_sms_template SET status = 'APPROVED' WHERE status = 'DRAFT' AND use_status = '1';
