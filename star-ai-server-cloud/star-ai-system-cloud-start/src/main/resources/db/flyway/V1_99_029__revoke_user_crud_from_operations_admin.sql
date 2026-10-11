-- =============================================================================
-- V1_99_029 · Revoke user-add/delete from operations_admin (C1 penetration fix)
-- -----------------------------------------------------------------------------
-- WHY THIS EXISTS
--   Penetration test found that operations_admin (role_code=operations_admin)
--   held system:user:add / system:user:delete / system:user:deleteBatch.
--   Any ops_admin token could create a NEW super_admin account via
--     POST /sys/user/add  ->  grant super_admin  ->  full takeover.
--   These three codes belong to super_admin ONLY. V1.0.20 seeded them to
--   operations_admin in error; this migration removes that grant.
--
-- SCOPE — WHAT THIS FILE DOES *NOT* CHANGE
--   * super_admin stays untouched — its grants are not modified here.
--   * system:user:edit / system:user:query — ops_admin CAN still read and
--     edit *basic* user info (KYC, contact, limit). Only CREATE and DELETE
--     are revoked.
--   * No DDL, no data loss, no role deletion.
--
-- IDEMPOTENT. Pure DML DELETE with safe guards — running twice is harmless.
--
-- AFTER APPLY (required, one-off)
--   ops_admin must LOG OUT and LOG IN again (Redis Permission_List cache
--   must be dropped). Sa-Token cache TTL = session TTL, so logout/login
--   is sufficient; no server restart needed.
--
-- ROLLBACK
--   docs/db/migrations/mysql/rollback/
--     V1_99_029__rollback_revoke_user_crud_from_operations_admin.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. SAFETY CHECK: abort (no-op) if operations_admin already lacks these grants.
--    The DELETE below is already safe, but this logs "nothing to do" clearly.
-- -----------------------------------------------------------------------------
-- NOTE: `sys_role` has NO `del_flag` column in this schema (see docs/db/mpay_system.sql:
--   id, role_name, role_code, description, create_by, create_time, update_by, update_time, tenant_id).
--   `role_code` carries a UNIQUE index (uniq_sys_role_role_code), so it already yields at most one row.
--   An earlier revision of this file filtered on `sys_role.del_flag`, which aborts with
--   ERROR 1054 (Unknown column 'del_flag') and therefore never reaches the revoke below.
SET @ops_role_id := (
  SELECT `id` FROM `sys_role`
  WHERE `role_code` = 'operations_admin'
  LIMIT 1
);

SET @existing_grants := (
  SELECT COUNT(*)
  FROM `sys_role_permission` rp
  JOIN `sys_permission` p ON p.`id` = rp.`permission_id` AND IFNULL(p.`del_flag`, 0) = 0
  WHERE rp.`role_id` = @ops_role_id
    AND p.`perms` IN (
      'system:user:add',
      'system:user:delete',
      'system:user:deleteBatch'
    )
);

SELECT
  @ops_role_id       AS operations_admin_role_id,
  @existing_grants   AS current_grants_before_revoke,
  'system:user:add / delete / deleteBatch' AS revoked_permissions;

-- -----------------------------------------------------------------------------
-- 2. REVOKE the three dangerous permission codes from operations_admin.
--    Delete JOIN ensures we only touch ops_admin + only these three codes.
-- -----------------------------------------------------------------------------
DELETE rp
FROM `sys_role_permission` rp
JOIN `sys_role`       r ON r.`id` = rp.`role_id` AND r.`role_code` = 'operations_admin'
JOIN `sys_permission` p ON p.`id` = rp.`permission_id`
WHERE p.`perms` IN (
    'system:user:add',
    'system:user:delete',
    'system:user:deleteBatch'
  )
  AND IFNULL(p.`del_flag`, 0) = 0;

-- -----------------------------------------------------------------------------
-- 3. GUARD: ensure super_admin still HOLDS these three codes.
--    If any is missing, insert it back (should never happen, but belts-and-suspenders).
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role_permission` (
  `id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`
)
SELECT REPLACE(UUID(), '-', ''), r.`id`, p.`id`, NULL, NOW(), '127.0.0.1'
FROM `sys_role` r
CROSS JOIN `sys_permission` p
WHERE r.`role_code` = 'super_admin'
  AND p.`perms` IN ('system:user:add', 'system:user:delete', 'system:user:deleteBatch')
  AND IFNULL(p.`del_flag`, 0) = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` x
    WHERE x.`role_id` = r.`id` AND x.`permission_id` = p.`id`
  );

-- -----------------------------------------------------------------------------
-- 4. VERIFY. ops_admin must now hold 0 of these three codes.
--    super_admin must hold all 3. If not, fail loudly so ops re-checks.
-- -----------------------------------------------------------------------------
SET @ops_after := (
  SELECT COUNT(*) FROM `sys_role_permission` rp
  JOIN `sys_permission` p ON p.`id` = rp.`permission_id` AND IFNULL(p.`del_flag`, 0) = 0
  JOIN `sys_role`       r ON r.`id` = rp.`role_id`       AND r.`role_code` = 'operations_admin'
  WHERE p.`perms` IN ('system:user:add', 'system:user:delete', 'system:user:deleteBatch')
);

SET @super_after := (
  SELECT COUNT(*) FROM `sys_role_permission` rp
  JOIN `sys_permission` p ON p.`id` = rp.`permission_id` AND IFNULL(p.`del_flag`, 0) = 0
  JOIN `sys_role`       r ON r.`id` = rp.`role_id`       AND r.`role_code` = 'super_admin'
  WHERE p.`perms` IN ('system:user:add', 'system:user:delete', 'system:user:deleteBatch')
);

SELECT
  @ops_after   AS ops_admin_grants_after_revoke,
  0            AS ops_expected,
  @super_after AS super_admin_grants_after_revoke,
  3            AS super_expected;

SET @fail := (@ops_after <> 0 OR @super_after <> 3);
SET @sql := IF(@fail,
  'SELECT 1 FROM `v1_99_029_VERIFY_FAILED_ops_revoke_user_crud`',
  'DO 0');
PREPARE vstmt FROM @sql; EXECUTE vstmt; DEALLOCATE PREPARE vstmt;

-- =============================================================================
-- Rollback (manual):
--
--   INSERT INTO sys_role_permission (id, role_id, permission_id)
--   SELECT REPLACE(UUID(), '-', ''), r.id, p.id
--     FROM sys_role r CROSS JOIN sys_permission p
--    WHERE r.role_code = 'operations_admin'
--      AND p.perms IN ('system:user:add','system:user:delete','system:user:deleteBatch');
--
-- Post-apply manual re-check:
--   SELECT r.role_code, p.perms
--     FROM sys_role_permission rp
--     JOIN sys_role r ON r.id = rp.role_id
--     JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE p.perms LIKE 'system:user:%' AND r.role_code IN ('operations_admin','super_admin')
--    ORDER BY r.role_code, p.perms;
-- =============================================================================
