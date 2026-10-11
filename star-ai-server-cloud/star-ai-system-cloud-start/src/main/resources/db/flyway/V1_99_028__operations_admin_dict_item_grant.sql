-- =============================================================================
-- V1_99_028 · Grant dict-item CRUD to operations_admin (C1 role matrix alignment)
-- -----------------------------------------------------------------------------
-- WHY THIS EXISTS
--   ops_admin (role_code = operations_admin) could not edit the business
--   dictionary behind the admin console, e.g. the UserLabel page
--   (/User/UserLabel -> dict_code='user_label', dict_id=1970678843978223617).
--   Two independent blockers existed:
--
--     (a) CODE -- SysDictItemController carried a class-level
--         @SaCheckRole({"admin","super_admin"}) that rejected operations_admin
--         even when it held the permission code. Fixed in
--         org.jeecg.modules.system.controller.SysDictItemController:
--         ROLE_OPS_ADMIN added to the gate; the C8 admin_role guard is untouched.
--
--     (b) DATA -- the four system:dict:item:* codes are legacy rows
--         (create_by='admin'). On environments whose operations_admin role was
--         created by the C1 seed (V1_99_023), those four rows were never granted,
--         so @SaCheckPermission("system:dict:item:edit") still answered 403.
--
--   This migration closes (b). It is the DATA half of the fix; (a) ships as code.
--
-- SCOPE -- WHAT THIS FILE DOES *NOT* CHANGE (security invariants preserved)
--   * C8 : editing the sys_config 'admin_role' dict item still requires
--          super_admin. It is enforced in
--          SysDictItemController.assertAdminRoleWriteAllowed()/ById(), NOT here,
--          and is re-verified by ramp_C_priv_esc_gates_v1.1.sh (C8-1).
--          Nothing in this file can widen the admin role set.
--   * C5 : the sys_table_white_list write path is untouched.
--   * C6 : no sensitive-table prefix and no sys_user column whitelist is touched.
--   * system:dict:item:* is business-dictionary CRUD only. It grants NO read on a
--     sensitive table: SensitiveTableBlacklistInterceptor + sys_table_white_list
--     stay the single enforcement point.
--
-- DELIVERY -- identical content, two delivery paths (both pipelines served)
--   1) ops scan : docs/db/migrations/mysql/V1_99_028__operations_admin_dict_item_grant.sql
--   2) Flyway   : jeecg-server-cloud/jeecg-system-cloud-start/src/main/resources/
--                 db/flyway/V1_99_028__operations_admin_dict_item_grant.sql
--   Version 1.99.28 is greater than every version already shipped in either
--   location (max = 1.99.027), so it can never be applied out of order.
--   Applying it twice (once by ops, once by Flyway) is harmless: idempotent.
--
-- IDEMPOTENT / RE-RUNNABLE. Pure DML, no DDL. Safe in any schema that owns
-- sys_role / sys_permission (and, for step 5, sys_user / sys_user_role).
--
-- AFTER APPLY (required, one-off)
--   Sa-Token caches the per-session 'Permission_List' / 'Role_List' in Redis.
--   ops_admin must LOG OUT and LOG IN again (or the session keys must be
--   dropped) before the new grants take effect; the code fix needs a restart.
--
-- ROLLBACK
--   docs/db/migrations/mysql/rollback/
--     V1_99_028__rollback_operations_admin_dict_item_grant.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Ensure the operations_admin role exists (canonical id per V1_99_023 C1).
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role` (
  `id`, `role_name`, `role_code`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`, `tenant_id`
)
SELECT
  'r_ops_u23_001', 'Operations Admin', 'operations_admin',
  'C1 ops: trade approval / limit config / FX rate config',
  '__C1_FLYWAY__', NOW(), '__C1_FLYWAY__', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role` WHERE `role_code` = 'operations_admin' LIMIT 1
);

-- -----------------------------------------------------------------------------
-- 2. Resolve the parent menu of the dict-item buttons (数据字典 / Data Dictionary).
--    Stays NULL on a schema that does not host the system menu tree; section 3
--    is then a no-op rather than creating orphaned button rows.
-- -----------------------------------------------------------------------------
SET @dict_menu_id := (
  SELECT `id` FROM `sys_permission`
  WHERE `id` = '1438782641187074050' AND IFNULL(`del_flag`, 0) = 0
  LIMIT 1
);
SET @dict_menu_id := IFNULL(
  @dict_menu_id,
  (
    SELECT `id` FROM `sys_permission`
    WHERE IFNULL(`del_flag`, 0) = 0
      AND (`url` = '/system/dict' OR `component` = 'system/dict/index')
    ORDER BY `create_time` ASC
    LIMIT 1
  )
);

-- -----------------------------------------------------------------------------
-- 3. Ensure the four system:dict:item:* codes exist and are enabled.
--    Canonical legacy ids are reused, so an already-seeded row is re-enabled
--    instead of duplicated.
-- -----------------------------------------------------------------------------
INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`,
  `del_flag`, `rule_flag`, `status`, `internal_or_external`
)
SELECT
  '1593160905216663554', @dict_menu_id,
  '{"zh_CN":"字典子项新增","en":"Add Dictionary Item"}',
  NULL, NULL, 0, NULL, NULL,
  2, 'system:dict:item:add', '1', 4.01, 0, NULL, 1,
  0, 0, 0, 'V1_99_028: dict-item add granted to operations_admin (C1 matrix)',
  'system', NOW(), 'system', NOW(), 0, 0, '1', 0
FROM DUAL
WHERE @dict_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_permission`
    WHERE (`id` = '1593160905216663554' OR `perms` = 'system:dict:item:add')
    LIMIT 1
  );

INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`,
  `del_flag`, `rule_flag`, `status`, `internal_or_external`
)
SELECT
  '1593160959633563650', @dict_menu_id,
  '{"zh_CN":"字典子项编辑","en":"Edit Dictionary Item"}',
  NULL, NULL, 0, NULL, NULL,
  2, 'system:dict:item:edit', '1', 4.02, 0, NULL, 1,
  0, 0, 0, 'V1_99_028: dict-item edit granted to operations_admin (C1 matrix)',
  'system', NOW(), 'system', NOW(), 0, 0, '1', 0
FROM DUAL
WHERE @dict_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_permission`
    WHERE (`id` = '1593160959633563650' OR `perms` = 'system:dict:item:edit')
    LIMIT 1
  );

INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`,
  `del_flag`, `rule_flag`, `status`, `internal_or_external`
)
SELECT
  '1593161025790320641', @dict_menu_id,
  '{"zh_CN":"字典子项删除","en":"Delete Dictionary Item"}',
  NULL, NULL, 0, NULL, NULL,
  2, 'system:dict:item:delete', '1', 4.03, 0, NULL, 1,
  0, 0, 0, 'V1_99_028: dict-item delete granted to operations_admin (C1 matrix)',
  'system', NOW(), 'system', NOW(), 0, 0, '1', 0
FROM DUAL
WHERE @dict_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_permission`
    WHERE (`id` = '1593161025790320641' OR `perms` = 'system:dict:item:delete')
    LIMIT 1
  );

INSERT INTO `sys_permission` (
  `id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`,
  `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`,
  `keep_alive`, `hidden`, `hide_tab`, `description`,
  `create_by`, `create_time`, `update_by`, `update_time`,
  `del_flag`, `rule_flag`, `status`, `internal_or_external`
)
SELECT
  '1593161089787011074', @dict_menu_id,
  '{"zh_CN":"字典子项批量删除","en":"Batch Delete Dictionary Items"}',
  NULL, NULL, 0, NULL, NULL,
  2, 'system:dict:item:deleteBatch', '1', 4.04, 0, NULL, 1,
  0, 0, 0, 'V1_99_028: dict-item batch-delete granted to operations_admin (C1 matrix)',
  'system', NOW(), 'system', NOW(), 0, 0, '1', 0
FROM DUAL
WHERE @dict_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_permission`
    WHERE (`id` = '1593161089787011074' OR `perms` = 'system:dict:item:deleteBatch')
    LIMIT 1
  );

-- Re-enable any of the four that was soft-deleted / disabled by earlier ops.
UPDATE `sys_permission`
SET `del_flag` = 0,
    `status` = '1',
    `update_by` = 'system',
    `update_time` = NOW()
WHERE `perms` IN (
    'system:dict:item:add',
    'system:dict:item:edit',
    'system:dict:item:delete',
    'system:dict:item:deleteBatch'
  )
  AND (IFNULL(`del_flag`, 0) <> 0 OR IFNULL(`status`, '0') <> '1');

-- -----------------------------------------------------------------------------
-- 4. Grant the four codes (and the parent menu) to operations_admin.
--    NOT EXISTS keeps it re-runnable; no pre-existing grant is duplicated.
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (
  `id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`
)
SELECT REPLACE(UUID(), '-', ''), r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r
JOIN `sys_permission` p
  ON p.`perms` IN (
       'system:dict:item:add',
       'system:dict:item:edit',
       'system:dict:item:delete',
       'system:dict:item:deleteBatch'
     )
WHERE r.`role_code` = 'operations_admin'
  AND IFNULL(p.`del_flag`, 0) = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` x
    WHERE x.`role_id` = r.`id` AND x.`permission_id` = p.`id`
  );

-- Parent menu (数据字典) — already granted on most environments; ensure it so
-- the menu renders for a freshly-created operations_admin role.
INSERT INTO `sys_role_permission` (
  `id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`
)
SELECT REPLACE(UUID(), '-', ''), r.`id`, @dict_menu_id, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r
WHERE r.`role_code` = 'operations_admin'
  AND @dict_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` x
    WHERE x.`role_id` = r.`id` AND x.`permission_id` = @dict_menu_id
  );

-- -----------------------------------------------------------------------------
-- 5. Bind the ops_admin account (by username, so it works whatever its id is)
--    to operations_admin. Additive and idempotent.
-- -----------------------------------------------------------------------------
INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`, `tenant_id`)
SELECT REPLACE(UUID(), '-', ''), u.`id`, r.`id`, 0
FROM `sys_user` u
JOIN `sys_role` r ON r.`role_code` = 'operations_admin'
WHERE u.`username` = 'ops_admin'
  AND IFNULL(u.`del_flag`, 0) = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` x
    WHERE x.`user_id` = u.`id` AND x.`role_id` = r.`id`
  );

-- -----------------------------------------------------------------------------
-- 6. Verification. The SELECT is informational (visible in the Flyway/ops log); the
--    guarded statement below aborts loudly when the grant did not take effect on a
--    schema that actually hosts the dict menu (i.e. the system schema).
-- -----------------------------------------------------------------------------
SET @grant_count := (
  SELECT COUNT(DISTINCT p.`perms`)
  FROM `sys_role_permission` rp
  JOIN `sys_role` r       ON r.`id` = rp.`role_id`
  JOIN `sys_permission` p ON p.`id` = rp.`permission_id`
  WHERE r.`role_code` = 'operations_admin'
    AND p.`perms` IN (
      'system:dict:item:add',
      'system:dict:item:edit',
      'system:dict:item:delete',
      'system:dict:item:deleteBatch'
    )
    AND IFNULL(p.`del_flag`, 0) = 0
);

SELECT @dict_menu_id AS dict_menu_id,
       @grant_count   AS operations_admin_dict_item_grants,
       4              AS expected_grants;

SET @verify_failed := (@dict_menu_id IS NOT NULL AND @grant_count < 4);
-- Fail-fast without SIGNAL: MySQL 8.0 refuses SIGNAL inside a prepared statement
-- (ERROR 1295 "not supported in the prepared statement protocol yet"), so the
-- failure is expressed as a read from a table that cannot exist. The identifier is
-- fixed and short on purpose (MySQL caps identifiers at 64 chars) and names the
-- problem; the counts that explain it are printed by the SELECT just above, e.g.
--   operations_admin_dict_item_grants = 0 | expected_grants = 4
--   ERROR 1146: Table '...v1_99_028_VERIFY_FAILED_operations_admin_dict_item_grants' doesn't exist
SET @sql_verify := IF(
  @verify_failed,
  'SELECT 1 FROM `v1_99_028_VERIFY_FAILED_operations_admin_dict_item_grants`',
  'DO 0'
);
PREPARE vstmt FROM @sql_verify;
EXECUTE vstmt;
DEALLOCATE PREPARE vstmt;

-- =============================================================================
-- Manual re-check (any time):
--   SELECT r.role_code, p.perms
--     FROM sys_role_permission rp
--     JOIN sys_role r       ON r.id = rp.role_id
--     JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE r.role_code = 'operations_admin'
--      AND p.perms LIKE 'system:dict:item:%';
--   -- expected: 4 rows
--
--   SELECT u.username, r.role_code
--     FROM sys_user_role ur
--     JOIN sys_user u ON u.id = ur.user_id
--     JOIN sys_role r ON r.id = ur.role_id
--    WHERE u.username = 'ops_admin';
--   -- expected: contains operations_admin
-- =============================================================================
