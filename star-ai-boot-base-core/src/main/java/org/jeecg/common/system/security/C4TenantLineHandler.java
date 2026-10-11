package org.jeecg.common.system.security;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import org.jeecg.common.constant.SecurityRoleConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.TenantConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * C4 tenant line: fail-closed when TenantContext is missing on <b>tenant-scoped</b> tables.
 * Tables without a {@code tenant_id} column must be ignored — otherwise MyBatis-Plus injects
 * an invalid predicate (Unknown column) or blocks legitimate global tables (e.g. sys_gateway_route).
 */
public class C4TenantLineHandler implements TenantLineHandler {

  private static final Logger log = LoggerFactory.getLogger(C4TenantLineHandler.class);

  public static final String C4_TENANT_CONTEXT_NULL = "C4_TENANT_CONTEXT_NULL";

  private static final Set<String> OWNER_ADMIN_ROLE_SET =
      new HashSet<>(
          Arrays.asList(
              "admin",
              SecurityRoleConstants.ROLE_OPS_ADMIN,
              SecurityRoleConstants.ROLE_FILE_ADMIN,
              SecurityRoleConstants.ROLE_SEC_ADMIN,
              SecurityRoleConstants.ROLE_SUPER_ADMIN));

  /**
   * Admin may skip tenant predicate on these (still tenant-scoped columns exist).
   */
  private static final Set<String> ADMIN_TENANT_IGNORE_WHITELIST =
      new HashSet<>(
          Arrays.asList(
              "sys_config",
              "sys_permission",
              "sys_role",
              "sys_dict",
              "sys_oss_config"));

  /**
   * Positive allowlist: only these tables currently have {@code tenant_id} in accept DB.
   * Expand when Flyway adds columns; until then all other tables are ignored.
   */
  private static final Set<String> TENANT_SCOPED_TABLES =
      new HashSet<>(
          Arrays.asList(
              "sys_announcement",
              "sys_category",
              "sys_data_source",
              "sys_depart",
              "sys_dict",
              "sys_files",
              "sys_log",
              "sys_position",
              "sys_role",
              "sys_tenant_pack",
              "sys_tenant_pack_user",
              "sys_third_account",
              "sys_third_app_config",
              "sys_user_mfa",
              "sys_user_role",
              "sys_user_tenant"));

  /** Avoid StpUtil.hasRole → getRoleList → SQL → ignoreTable → hasRole recursion. */
  private static final ThreadLocal<Boolean> IN_ADMIN_ROLE_CHECK = new ThreadLocal<>();

  @Override
  public Expression getTenantId() {
    // Prefer getTenant() — jeecg-boot-common TenantContext may win on classpath (no getTenantId).
    String tenantId = TenantContext.getTenant();
    if (tenantId == null || tenantId.trim().isEmpty()) {
      log.error("[SEC-C4] tenant context is null, bypass blocked");
      throw new JeecgBootException(C4_TENANT_CONTEXT_NULL);
    }
    try {
      return new LongValue(Long.parseLong(tenantId));
    } catch (NumberFormatException e) {
      return new NullValue();
    }
  }

  @Override
  public String getTenantIdColumn() {
    return TenantConstant.TENANT_ID_TABLE;
  }

  @Override
  public boolean ignoreTable(String tableName) {
    if (tableName == null) {
      return true;
    }
    String tn = tableName.trim().toLowerCase();
    if (!TENANT_SCOPED_TABLES.contains(tn)) {
      return true;
    }
    if (tn.startsWith("qrtz_")) {
      return true;
    }
    if (hasAnyOwnerAdminRole() && ADMIN_TENANT_IGNORE_WHITELIST.contains(tn)) {
      return true;
    }
    return false;
  }

  private boolean hasAnyOwnerAdminRole() {
    if (Boolean.TRUE.equals(IN_ADMIN_ROLE_CHECK.get())) {
      return false;
    }
    IN_ADMIN_ROLE_CHECK.set(Boolean.TRUE);
    try {
      if (!StpUtil.isLogin()) {
        return false;
      }
      for (String role : OWNER_ADMIN_ROLE_SET) {
        if (StpUtil.hasRole(role)) {
          return true;
        }
      }
    } catch (Exception ignore) {
    } finally {
      IN_ADMIN_ROLE_CHECK.remove();
    }
    return false;
  }
}
