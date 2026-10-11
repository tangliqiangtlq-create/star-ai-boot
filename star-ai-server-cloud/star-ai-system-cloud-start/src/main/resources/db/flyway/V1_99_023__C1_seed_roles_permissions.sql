-- ======================================================================================
-- C1-1: 11 ROLES (idempotent INSERT ... WHERE NOT EXISTS)
-- Align columns with Jeecg sys_role (NO status column — see SysRole.java / V1.0.91)
-- ======================================================================================
INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_ops_u23_001', 'Operations Admin', 'operations_admin', 'C1 ops: trade approval / limit config / FX rate config', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'operations_admin' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_tpl_u23_002', 'Template Approver', 'template_approver', 'C1 templates: message/voucher/KYC template review publish', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'template_approver' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_fil_u23_003', 'File Admin', 'file_admin', 'C1 files: OSS KYC/PROOF/STATEMENT full CRUD', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'file_admin' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_sec_u23_004', 'Security Audit Admin', 'sec_admin', 'C1 security: audit log / probe keys / blacklist / user lock', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'sec_admin' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_adm_old_005', 'Platform Admin', 'admin', 'C1 legacy role kept (existing users)', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'admin' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_ag1_old_006', 'Agent L1', 'agent1', 'C1 legacy role', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'agent1' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_ag2_old_007', 'Agent L2', 'agent2', 'C1 legacy role', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'agent2' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_chk_old_008', 'Finance Checker', 'CHECKER', 'C1 legacy role', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'CHECKER' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_mac_old_009', 'Finance Maker', 'MACKER', 'C1 legacy role', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'MACKER' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_sup_priv_010', 'Super Admin', 'super_admin', 'C1 super: full perms, limited to 2 users (CISO/CTO)', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'super_admin' LIMIT 1);

INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_own_old_011', 'Owner', 'OWNER', 'C1 tenant owner: same as creator perms (class B included)', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'OWNER' LIMIT 1);

-- ======================================================================================
-- C1-2: PERMISSION DICT full 54-row matrix (Jeecg sys_permission columns; menu_type=2 button)
-- 54 = 5 remittance + 3 withdraw + 3 transfer + 5 account + 4 file + 5 process + 5 job + 5 template + 6 user + 4 security + 4 ops + 6 deposit
-- ======================================================================================
INSERT IGNORE INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`
) VALUES
 -- 5 remittance
 ('p_dcs_01', '', 'Remittance Approve', NULL, NULL, 0, NULL, NULL, 2, 'dcs:dcs_remittance_record:approve', '1', 10, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dcs_02', '', 'Remittance Reject', NULL, NULL, 0, NULL, NULL, 2, 'dcs:dcs_remittance_record:reject',  '1', 11, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dcs_03', '', 'Remittance View', NULL, NULL, 0, NULL, NULL, 2, 'dcs:dcs_remittance_record:view',    '1', 12, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dcs_04', '', 'Remittance List', NULL, NULL, 0, NULL, NULL, 2, 'dcs:dcs_remittance_record:list',    '1', 13, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dcs_05', '', 'Remittance Export', NULL, NULL, 0, NULL, NULL, 2, 'dcs:dcs_remittance_record:export',  '1', 14, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 3 withdrawal
 ('p_wdr_06', '', 'Withdrawal Confirm', NULL, NULL, 0, NULL, NULL, 2, 'dcs:withdrawal_record:confirm', '1', 20, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_wdr_07', '', 'Withdrawal View', NULL, NULL, 0, NULL, NULL, 2, 'dcs:withdrawal_record:view',    '1', 21, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_wdr_08', '', 'Withdrawal List', NULL, NULL, 0, NULL, NULL, 2, 'dcs:withdrawal_record:list',    '1', 22, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 3 transfer
 ('p_tfr_09', '', 'Transfer Confirm', NULL, NULL, 0, NULL, NULL, 2, 'dcs:transfer_record:confirm', '1', 30, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_tfr_10', '', 'Transfer View', NULL, NULL, 0, NULL, NULL, 2, 'dcs:transfer_record:view',    '1', 31, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_tfr_11', '', 'Transfer List', NULL, NULL, 0, NULL, NULL, 2, 'dcs:transfer_record:list',    '1', 32, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 5 account
 ('p_acc_12', '', 'Account List', NULL, NULL, 0, NULL, NULL, 2, 'dcs:account:list',             '1', 40, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_acc_13', '', 'Balance View', NULL, NULL, 0, NULL, NULL, 2, 'dcs:account:balance',          '1', 41, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_acc_14', '', 'Freeze Account', NULL, NULL, 0, NULL, NULL, 2, 'dcs:account:freeze',           '1', 42, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_acc_15', '', 'Unfreeze Account', NULL, NULL, 0, NULL, NULL, 2, 'dcs:account:unfreeze',         '1', 43, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_acc_16', '', 'Statement Export', NULL, NULL, 0, NULL, NULL, 2, 'dcs:account:statement_export', '1', 44, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 4 file
 ('p_oss_17', '', 'File Upload', NULL, NULL, 0, NULL, NULL, 2, 'system:ossFile:upload',   '1', 50, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_oss_18', '', 'File Download', NULL, NULL, 0, NULL, NULL, 2, 'system:ossFile:download', '1', 51, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_oss_19', '', 'File Delete', NULL, NULL, 0, NULL, NULL, 2, 'system:ossFile:delete',   '1', 52, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_oss_20', '', 'File View', NULL, NULL, 0, NULL, NULL, 2, 'system:ossFile:view',     '1', 53, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 5 process
 ('p_pe_21', '', 'Process Start', NULL, NULL, 0, NULL, NULL, 2, 'processengine:sys_process_instance:add',       '1', 60, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_pe_22', '', 'Process View', NULL, NULL, 0, NULL, NULL, 2, 'processengine:sys_process_instance:view',      '1', 61, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_pe_23', '', 'Process Send Event', NULL, NULL, 0, NULL, NULL, 2, 'processengine:sys_process_instance:sendEvent', '1', 62, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_pe_24', '', 'Process Rollback', NULL, NULL, 0, NULL, NULL, 2, 'processengine:sys_process_instance:rollback',  '1', 63, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_pe_25', '', 'Process List', NULL, NULL, 0, NULL, NULL, 2, 'processengine:sys_process_instance:list',      '1', 64, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 5 job monitor
 ('p_job_26', '', 'Job List', NULL, NULL, 0, NULL, NULL, 2, 'system:quartzJob:list',    '1', 70, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_job_27', '', 'Job Add', NULL, NULL, 0, NULL, NULL, 2, 'system:quartzJob:add',     '1', 71, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_job_28', '', 'Job Pause', NULL, NULL, 0, NULL, NULL, 2, 'system:quartzJob:pause',   '1', 72, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_job_29', '', 'Job Run Now', NULL, NULL, 0, NULL, NULL, 2, 'system:quartzJob:runNow',  '1', 73, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_job_30', '', 'Job Monitor', NULL, NULL, 0, NULL, NULL, 2, 'system:quartzJob:monitor', '1', 74, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 5 template
 ('p_tpl_31', '', 'Template Add', NULL, NULL, 0, NULL, NULL, 2, 'system:template:add',     '1', 80, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_tpl_32', '', 'Template Edit', NULL, NULL, 0, NULL, NULL, 2, 'system:template:edit',    '1', 81, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_tpl_33', '', 'Template Approve', NULL, NULL, 0, NULL, NULL, 2, 'system:template:approve', '1', 82, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_tpl_34', '', 'Template Publish', NULL, NULL, 0, NULL, NULL, 2, 'system:template:publish', '1', 83, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_tpl_35', '', 'Template List', NULL, NULL, 0, NULL, NULL, 2, 'system:template:list',    '1', 84, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 6 user
 ('p_usr_36', '', 'User List', NULL, NULL, 0, NULL, NULL, 2, 'system:user:list',          '1', 90, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_usr_37', '', 'User Edit', NULL, NULL, 0, NULL, NULL, 2, 'system:user:edit',          '1', 91, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_usr_38', '', 'Reset Password', NULL, NULL, 0, NULL, NULL, 2, 'system:user:resetPwd',      '1', 92, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_usr_39', '', 'Lock Unlock User', NULL, NULL, 0, NULL, NULL, 2, 'system:user:lock',          '1', 93, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_usr_40', '', 'Role Assign', NULL, NULL, 0, NULL, NULL, 2, 'system:user:roleAssign',    '1', 94, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_usr_41', '', 'User Read-Only Check', NULL, NULL, 0, NULL, NULL, 2, 'system:user:checkOnlyUser', '1', 95, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 4 security
 ('p_sec_42', '', 'Probe API Key View Secret', NULL, NULL, 0, NULL, NULL, 2, 'depositScan:apikey:viewSecret', '1', 100, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_sec_43', '', 'Probe API Key Rotate', NULL, NULL, 0, NULL, NULL, 2, 'depositScan:apikey:rotate',     '1', 101, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_sec_44', '', 'IP Blacklist Edit', NULL, NULL, 0, NULL, NULL, 2, 'system:ipBlacklist:edit',       '1', 102, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_sec_45', '', 'Audit Log List', NULL, NULL, 0, NULL, NULL, 2, 'system:auditLog:list',          '1', 103, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 4 ops
 ('p_ops_46', '', 'FX Rate Config', NULL, NULL, 0, NULL, NULL, 2, 'dcs:currency:editRate',        '1', 110, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_ops_47', '', 'Limit Config', NULL, NULL, 0, NULL, NULL, 2, 'dcs:transactionLimit:edit',    '1', 111, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_ops_48', '', 'Agent Application Approve', NULL, NULL, 0, NULL, NULL, 2, 'dcs:agentApplication:approve', '1', 112, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_ops_49', '', 'Agent List', NULL, NULL, 0, NULL, NULL, 2, 'dcs:agent:list',               '1', 113, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 -- 6 deposit
 ('p_dep_50', '', 'Deposit List', NULL, NULL, 0, NULL, NULL, 2, 'dcs:deposit_record:list',           '1', 120, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dep_51', '', 'Deposit Confirm', NULL, NULL, 0, NULL, NULL, 2, 'dcs:deposit_record:confirm',        '1', 121, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dep_52', '', 'Deposit Address Pool Edit', NULL, NULL, 0, NULL, NULL, 2, 'dcs:depositAddressPool:edit',      '1', 122, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dep_53', '', 'Deposit Scan Log View', NULL, NULL, 0, NULL, NULL, 2, 'dcs:depositScanLog:view',           '1', 123, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dep_54', '', 'Deposit Currency Whitelist Edit', NULL, NULL, 0, NULL, NULL, 2, 'dcs:depositCurrencyWhitelist:edit', '1', 124, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0),
 ('p_dep_55', '', 'KYC Review', NULL, NULL, 0, NULL, NULL, 2, 'dcs:kycRecord:review',              '1', 125, 0, NULL, 1, 0, 1, 0, 'C1 perm', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0, 0, '1', 0);

-- ======================================================================================
-- C1-3: ROLE-PERMISSION grant matrix (permission_id column, not perm_id)
-- ======================================================================================
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin','CHECKER')
  AND p.perms LIKE 'dcs:dcs_remittance_record:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin','CHECKER','MACKER')
  AND p.perms LIKE 'dcs:withdrawal_record:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin','CHECKER','MACKER')
  AND p.perms LIKE 'dcs:transfer_record:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin','CHECKER','MACKER','agent2')
  AND p.perms LIKE 'dcs:account:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','file_admin','super_admin')
  AND p.perms LIKE 'system:ossFile:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin','CHECKER')
  AND p.perms LIKE 'processengine:sys_process_instance:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','sec_admin','super_admin')
  AND p.perms LIKE 'system:quartzJob:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','template_approver','super_admin','operations_admin')
  AND p.perms LIKE 'system:template:%' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','super_admin')
  AND p.perms LIKE 'system:user:%' AND p.perms<>'system:user:checkOnlyUser' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code='sec_admin' AND p.perms='system:user:checkOnlyUser' AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','sec_admin','super_admin')
  AND (p.perms LIKE 'depositScan:apikey:%' OR p.perms LIKE 'system:ipBlacklist:%' OR p.perms LIKE 'system:auditLog:%')
  AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin')
  AND (p.perms LIKE 'dcs:currency:%' OR p.perms LIKE 'dcs:transactionLimit:%' OR p.perms LIKE 'dcs:agentApplication:%' OR p.perms='dcs:agent:list')
  AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r JOIN `sys_permission` p ON 1=1
WHERE r.role_code IN ('admin','operations_admin','super_admin','CHECKER','MACKER','file_admin','sec_admin','agent2')
  AND (p.perms LIKE 'dcs:deposit_%' OR p.perms='dcs:kycRecord:review')
  AND p.create_by='__C1_FLYWAY__'
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` x WHERE x.role_id=r.id AND x.permission_id=p.id);

-- Verify:
-- SELECT COUNT(*) FROM sys_permission WHERE id LIKE 'p_%' AND create_by='__C1_FLYWAY__'; -- =54
