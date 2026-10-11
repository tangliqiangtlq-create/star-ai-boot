package org.jeecg.config.oss;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import org.jeecg.common.constant.SecurityRoleConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jeecg.common.system.vo.LoginUser;

/**
 * Small helper that makes sure every OssFile row is bound to a userId + tenantId before DB write,
 * and offers file_admin role constants used by OssFileServiceImpl access checks.
 *
 * <p>This class lives in base-core so it can be referenced from the tenant interceptor and the
 * x-file-storage driver.  To avoid introducing a cycle (base-core -> system-biz oss entity)
 * the owner/tenant filler accepts {@link Object} and reflects setUserId/setTenantId.
 */
public final class OssTenantOwnerUtil {

  public static final List<String> FILE_ADMIN_ROLES =
      Collections.unmodifiableList(
          Arrays.asList(
              "ops_admin",
              SecurityRoleConstants.ROLE_OPS_ADMIN,
              SecurityRoleConstants.ROLE_SEC_ADMIN,
              SecurityRoleConstants.ROLE_FILE_ADMIN,
              "admin"));

  private OssTenantOwnerUtil() {}

  public static boolean isInternalCallContext() {
    try {
      Object userObj = StpUtil.getSession().get("loginUser");
      if (userObj instanceof LoginUser) {
        return "internal_call".equalsIgnoreCase(((LoginUser) userObj).getUsername());
      }
    } catch (Exception ignore) {
    }
    return false;
  }

  public static String currentLoginUserId() {
    try {
      if (StpUtil.isLogin()) {
        String uid = StpUtil.getLoginIdAsString();
        if (StrUtil.isNotBlank(uid)) {
          return uid;
        }
      }
    } catch (Exception ignore) {
    }
    Object userObj = null;
    try {
      userObj = StpUtil.getSession().get("loginUser");
    } catch (Exception ignore) {
    }
    if (userObj instanceof LoginUser) {
      String id = ((LoginUser) userObj).getId();
      if (StrUtil.isNotBlank(id)) {
        return id;
      }
    }
    if (isInternalCallContext()) {
      return "__INTERNAL_CALL__";
    }
    return null;
  }

  public static Integer currentTenantId() {
    try {
      Object t = StpUtil.getSession().get("tenantId");
      if (t instanceof Number) {
        return ((Number) t).intValue();
      }
      if (t instanceof String && StrUtil.isNumeric((String) t)) {
        return Integer.parseInt((String) t);
      }
    } catch (Exception ignore) {
    }
    try {
      Object raw = org.jeecg.common.config.TenantContext.getTenant();
      if (raw != null && !"".equals(raw)) {
        return Integer.parseInt(String.valueOf(raw));
      }
    } catch (Exception ignore) {
    }
    return null;
  }

  /**
   * Reflectively set userId / tenantId on an OssFile (or any entity) when the fields are absent.
   * We do not import OssFile here because it lives in jeecg-system-biz and importing it would
   * create a cycle between base-core and system-biz.
   */
  public static void fillOwnerIfAbsent(Object entity) {
    if (entity == null) {
      return;
    }
    try {
      java.lang.reflect.Method g = entity.getClass().getMethod("getUserId");
      Object cur = g.invoke(entity);
      if (cur == null || (cur instanceof CharSequence && StrUtil.isBlank((CharSequence) cur))) {
        java.lang.reflect.Method s = entity.getClass().getMethod("setUserId", String.class);
        s.invoke(entity, currentLoginUserId());
      }
    } catch (NoSuchMethodException ignore) {
      // not an OssFile-like entity
    } catch (Exception e) {
      throw new IllegalStateException("oss fillOwnerIfAbsent failed", e);
    }
    try {
      java.lang.reflect.Method g = entity.getClass().getMethod("getTenantId");
      Object cur = g.invoke(entity);
      if (cur == null) {
        Integer t = currentTenantId();
        if (t != null) {
          java.lang.reflect.Method s;
          try {
            s = entity.getClass().getMethod("setTenantId", Integer.class);
          } catch (NoSuchMethodException nsme) {
            s = entity.getClass().getMethod("setTenantId", Long.class);
            s.invoke(entity, t.longValue());
            return;
          }
          s.invoke(entity, t);
        }
      }
    } catch (NoSuchMethodException ignore) {
      // no tenantId column on entity
    } catch (Exception e) {
      throw new IllegalStateException("oss fillOwnerIfAbsent tenantId failed", e);
    }
  }
}
