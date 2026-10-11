package org.jeecg.common.util;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import java.util.Collection;
import java.util.LinkedHashSet;
import org.jeecg.common.api.CommonAPI;

public final class RoleConfigUtil {
  private static final String[] DEFAULT_ADMIN_ROLES = {
    "admin", "ADMIN", "administrator", "ROLE_ADMIN", "operations_admin", "configuration_admin"
  };

  private static final String SYS_CONFIG_DICT_CODE = "sys_config";
  private static final String ADMIN_ROLE_KEY = "admin_role";
  private static final long ADMIN_ROLES_CACHE_MS = 5 * 60 * 1000L;

  private static volatile long adminRolesCacheAtMs;
  private static volatile String[] cachedAdminRoles;

  private RoleConfigUtil() {}

  public static boolean isAdmin(CommonAPI commonAPI) {
    return hasAnyRole(resolveAdminRoles(commonAPI));
  }

  /** Determine whether the Role code belongs to the configured administrator role set. */
  public static boolean isConfiguredAdminRole(CommonAPI commonAPI, String roleCode) {
    if (StrUtil.isBlank(roleCode)) {
      return false;
    }
    for (String role : resolveAdminRoles(commonAPI)) {
      if (roleCode.equals(role)) {
        return true;
      }
    }
    return false;
  }

  /** Determines whether the given role collection contains any administrator roles. */
  public static boolean rolesContainAdmin(CommonAPI commonAPI, Collection<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return false;
    }
    for (String role : resolveAdminRoles(commonAPI)) {
      if (roles.contains(role)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAnyRole(String... roles) {
    if (roles == null || roles.length == 0) {
      return false;
    }
    for (String role : roles) {
      if (StpUtil.hasRole(role)) {
        return true;
      }
    }
    return false;
  }

  private static String[] resolveAdminRoles(CommonAPI commonAPI) {
    long now = System.currentTimeMillis();
    String[] cached = cachedAdminRoles;
    if (cached != null && now - adminRolesCacheAtMs <= ADMIN_ROLES_CACHE_MS) {
      return cached;
    }

    String configuredRoles = null;
    if (commonAPI != null) {
      try {
        configuredRoles = commonAPI.translateDict(SYS_CONFIG_DICT_CODE, ADMIN_ROLE_KEY);
      } catch (Exception ignored) {
      }
    }

    String[] resolved = mergeRoles(DEFAULT_ADMIN_ROLES, splitRoles(configuredRoles));
    cachedAdminRoles = resolved;
    adminRolesCacheAtMs = now;
    return resolved;
  }

  private static String[] splitRoles(String roles) {
    if (StrUtil.isBlank(roles)) {
      return new String[0];
    }
    String normalized = roles.replace("，", ",");
    String[] parts = normalized.split(",");
    int count = 0;
    for (String part : parts) {
      if (StrUtil.isNotBlank(part)) {
        count++;
      }
    }
    if (count == 0) {
      return new String[0];
    }
    String[] result = new String[count];
    int idx = 0;
    for (String part : parts) {
      String trimmed = StrUtil.trim(part);
      if (StrUtil.isNotBlank(trimmed)) {
        result[idx++] = trimmed;
      }
    }
    return result;
  }

  private static String[] mergeRoles(String[] base, String[] extra) {
    if (extra == null || extra.length == 0) {
      return base;
    }
    LinkedHashSet<String> mergedSet = new LinkedHashSet<>(base.length + extra.length);
    for (String role : base) {
      if (StrUtil.isNotBlank(role)) {
        mergedSet.add(role);
      }
    }
    for (String role : extra) {
      if (StrUtil.isNotBlank(role)) {
        mergedSet.add(role);
      }
    }
    return mergedSet.toArray(new String[0]);
  }
}
