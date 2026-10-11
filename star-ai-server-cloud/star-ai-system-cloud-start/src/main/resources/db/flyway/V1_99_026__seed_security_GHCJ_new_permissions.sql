-- ============================================================================
-- V1_99_026 = classpath copy of docs/db/migrations/mysql/V1.0.92 (system Flyway locations)
-- V1.0.92 Seed GHCJ 8 new button permissions (G-2FA / H-Audit / C-LedgerGuard / J-OpsDeploy)
-- ----------------------------------------------------------------------------
-- Purpose: Solve P0-8 in Diff Report V2.0: super_admin first login → all buttons grey
--   because 8 new permissions from G/H/C/J batches were never seeded into DB.
-- Scope  : 4 categories, 8 button-only permissions (menu_type=2, hidden=1)
--   G(2FA)             : *:sysUser2fa:email:* , *:sysUser2fa:verify:*
--   H(Audit deny)      : *:auditDeny:list:*    , *:auditDeny:export:*
--   C(Ledger Guard)    : dcs:ledger:manualAdjust:* , dcs:rateEdit:* , dcs:deposit:forceConfirm:*
--   J(Ops deploy view) : ops:deploy:view:*
-- Parent strategy: 4 virtual hidden category nodes (secG01 / secH01 / secC01 / secJ01)
--   mounted under root (parent_id=''), all hidden=1 so they will NOT appear in
--   the front-end menu tree (button-only permissions never render as menus anyway).
-- Idempotent  : INSERT IGNORE + NOT EXISTS guard → repeatable safe.
-- Rollback    : DELETE FROM sys_role_permission WHERE permission_id LIKE 'sec%';
--               DELETE FROM sys_permission WHERE id LIKE 'sec%';
-- ============================================================================

-- ---------------------------------------------------------
-- 0. Four virtual hidden parent category nodes (menu_type=0)
--    Placeholder roots for the 8 permission buttons.
-- ---------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`
) VALUES
  ('secG01', '', '{"zh_CN":"Security-G-2FA-Group","en":"Security-G-2FA-Group"}', NULL, NULL, 0, NULL, NULL, 0, NULL, '1', 92.01, 0, NULL, 0, 0, 1, 0, 'J10 V1.0.92: G-class 2FA button perms virtual parent (hidden)', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secH01', '', '{"zh_CN":"Security-H-Audit-Group","en":"Security-H-Audit-Group"}',   NULL, NULL, 0, NULL, NULL, 0, NULL, '1', 92.02, 0, NULL, 0, 0, 1, 0, 'J10 V1.0.92: H-class audit-deny button perms virtual parent (hidden)', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secC01', '', '{"zh_CN":"Security-C-Ledger-Group","en":"Security-C-Ledger-Group"}', NULL, NULL, 0, NULL, NULL, 0, NULL, '1', 92.03, 0, NULL, 0, 0, 1, 0, 'J10 V1.0.92: C-class OwnerOrAdminGuard ledger button perms virtual parent (hidden)', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secJ01', '', '{"zh_CN":"Security-J-Ops-Group","en":"Security-J-Ops-Group"}',    NULL, NULL, 0, NULL, NULL, 0, NULL, '1', 92.04, 0, NULL, 0, 0, 1, 0, 'J10 V1.0.92: J-class ops deploy-view button perms virtual parent (hidden)', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------
-- 1. 8 Button permissions (menu_type=2 hidden=1 perms_type=1)
-- ---------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`
) VALUES
  -- G-class 2FA (2 items): G9 zero-param 2FA email send + verify
  ('secG02FaEmail',  'secG01', '2FA Email Send',       NULL, NULL, 0, NULL, NULL, 2, '*:sysUser2fa:email:*',        '1', 9211, 0, NULL, 1, 0, 1, 0, 'J10-G: zero-param 2FA email API (/sys/user/2fa/email GET+POST) guard perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secG02FaVerify', 'secG01', '2FA Code Verify',     NULL, NULL, 0, NULL, NULL, 2, '*:sysUser2fa:verify:*',       '1', 9212, 0, NULL, 1, 0, 1, 0, 'J10-G: 2FA verify API guard perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  -- H-class audit-deny (2 items): H2 Feign degrade audit list/export
  ('secHAuditDenyList',   'secH01', 'Audit Deny List', NULL, NULL, 0, NULL, NULL, 2, '*:auditDeny:list:*',          '1', 9221, 0, NULL, 1, 0, 1, 0, 'J10-H: audit-deny degrade log list query perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secHAuditDenyExport', 'secH01', 'Audit Deny Export', NULL, NULL, 0, NULL, NULL, 2, '*:auditDeny:export:*',        '1', 9222, 0, NULL, 1, 0, 1, 0, 'J10-H: audit-deny degrade log Excel export perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  -- C-class ledger Guard (3 items): OwnerOrAdminGuard ledger/rate/deposit sensitive ops
  ('secCLedgerAdj',    'secC01', 'Ledger Manual Adjust',    NULL, NULL, 0, NULL, NULL, 2, 'dcs:ledger:manualAdjust:*',   '1', 9231, 0, NULL, 1, 0, 1, 0, 'J10-C: LedgerWriteGuard manual balance adjust (admin/operations_admin) perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secCRateEdit',     'secC01', 'Direct Rate Edit',  NULL, NULL, 0, NULL, NULL, 2, 'dcs:rateEdit:*',              '1', 9232, 0, NULL, 1, 0, 1, 0, 'J10-C: manual rate edit (bypass lock) OwnerOrAdminGuard perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  ('secCDepositForce', 'secC01', 'Deposit Force Confirm',    NULL, NULL, 0, NULL, NULL, 2, 'dcs:deposit:forceConfirm:*',  '1', 9233, 0, NULL, 1, 0, 1, 0, 'J10-C: force-confirm deposit when txHash not on-chain perms', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0),
  -- J-class ops deploy view (1 item): read-only deploy info (no execute)
  ('secJOpsDeployView', 'secJ01', 'Ops Deploy Overview Read', NULL, NULL, 0, NULL, NULL, 2, 'ops:deploy:view:*',           '1', 9241, 0, NULL, 1, 0, 1, 0, 'J10-J: ops docker/image/env deploy info read-only perms (no exec/restart)', 'system', NOW(), 'system', NOW(), 0, 0, '1', 0);

-- ---------------------------------------------------------
-- 1b. Ensure dcs_admin role exists (not in V1_99_023 11-role set)
-- ---------------------------------------------------------
INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`, `create_by`, `create_time`,
  `update_by`, `update_time`, `tenant_id`
)
SELECT 'r_dcs_u26_012', 'DCS Admin', 'dcs_admin', 'C1/V1.0.92: DCS funds ops (ledger/rate/deposit)', '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code` = 'dcs_admin' LIMIT 1);

-- ---------------------------------------------------------
-- 2. Grant admin + dcs_admin + operations_admin roles (idempotent IGNORE)
--    3 roles × 8 perms = 24 rows (duplicates silently skipped by IGNORE + NOT EXISTS)
-- ---------------------------------------------------------

-- admin (super_admin fallback: always grant all 8)
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.perms IN (
    '*:sysUser2fa:email:*', '*:sysUser2fa:verify:*',
    '*:auditDeny:list:*', '*:auditDeny:export:*',
    'dcs:ledger:manualAdjust:*', 'dcs:rateEdit:*', 'dcs:deposit:forceConfirm:*',
    'ops:deploy:view:*'
  )
WHERE r.role_code = 'admin'
  
  AND IFNULL(p.del_flag, 0) = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` x
    WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- operations_admin (grant H/C/J 7 items: no 2FA user self-service)
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.perms IN (
    '*:auditDeny:list:*', '*:auditDeny:export:*',
    'dcs:ledger:manualAdjust:*', 'dcs:rateEdit:*', 'dcs:deposit:forceConfirm:*',
    'ops:deploy:view:*'
  )
WHERE r.role_code = 'operations_admin'
  
  AND IFNULL(p.del_flag, 0) = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` x
    WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- dcs_admin (grant C 3 ledger/rate/deposit items only)
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.perms IN (
    'dcs:ledger:manualAdjust:*', 'dcs:rateEdit:*', 'dcs:deposit:forceConfirm:*'
  )
WHERE r.role_code = 'dcs_admin'
  
  AND IFNULL(p.del_flag, 0) = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` x
    WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- ============================================================
-- Verify queries (run after Flyway migrate to confirm seed OK):
--
-- J-GATE-12-2: 8 VALUES rows actually inserted
--   SELECT id, perms, menu_type, hidden FROM sys_permission
--    WHERE id IN ('secG02FaEmail','secG02FaVerify','secHAuditDenyList','secHAuditDenyExport',
--                 'secCLedgerAdj','secCRateEdit','secCDepositForce','secJOpsDeployView');
--   → expect 8 rows
--
-- J-GATE-12-3: admin grant successful
--   SELECT r.role_code, COUNT(*) AS cnt
--     FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE p.perms IN ('*:sysUser2fa:email:*','*:sysUser2fa:verify:*',
--                      '*:auditDeny:list:*','*:auditDeny:export:*',
--                      'dcs:ledger:manualAdjust:*','dcs:rateEdit:*','dcs:deposit:forceConfirm:*',
--                      'ops:deploy:view:*')
--    GROUP BY r.role_code;
--   → expect admin=8 / operations_admin=6 / dcs_admin=3
-- ============================================================
