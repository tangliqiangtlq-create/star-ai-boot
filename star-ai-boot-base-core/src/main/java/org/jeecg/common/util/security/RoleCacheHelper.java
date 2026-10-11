package org.jeecg.common.util.security;

import cn.dev33.satoken.stp.StpUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class RoleCacheHelper {

  private static final Cache<String, Set<String>> ROLE_SET =
      CacheBuilder.newBuilder()
          .maximumSize(100_000)
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build();

  private static String currentKey() {
    try {
      Object loginId = StpUtil.getLoginIdDefaultNull();
      if (loginId == null) {
        return null;
      }
      Object tenantId = StpUtil.getLoginDevice();
      String tenant = tenantId == null ? "-" : String.valueOf(tenantId);
      return tenant + ":" + String.valueOf(loginId);
    } catch (Exception ignore) {
      return null;
    }
  }

  public static Set<String> getCurrentRoleSet() {
    String key = currentKey();
    if (key == null) {
      return Collections.emptySet();
    }
    Set<String> cached = ROLE_SET.getIfPresent(key);
    if (cached != null) {
      return cached;
    }
    Set<String> fresh = loadCurrentRoles();
    ROLE_SET.put(key, fresh);
    return fresh;
  }

  private static Set<String> loadCurrentRoles() {
    try {
      List<String> list = StpUtil.getRoleList();
      if (list == null || list.isEmpty()) {
        return Collections.emptySet();
      }
      Set<String> set = new HashSet<>(list.size());
      for (String r : list) {
        if (r != null) {
          set.add(r);
        }
      }
      return Collections.unmodifiableSet(set);
    } catch (Exception ignore) {
      return Collections.emptySet();
    }
  }

  public static boolean hasAnyRole(Collection<String> requiredRoles) {
    if (requiredRoles == null || requiredRoles.isEmpty()) {
      return false;
    }
    Set<String> mine = getCurrentRoleSet();
    if (mine.isEmpty()) {
      return false;
    }
    for (String r : requiredRoles) {
      if (r == null) {
        continue;
      }
      if (mine.contains(r)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasAnyRole(String... requiredRoles) {
    if (requiredRoles == null || requiredRoles.length == 0) {
      return false;
    }
    Set<String> mine = getCurrentRoleSet();
    if (mine.isEmpty()) {
      return false;
    }
    for (String r : requiredRoles) {
      if (r == null) {
        continue;
      }
      if (mine.contains(r)) {
        return true;
      }
    }
    return false;
  }

  public static void bustUser(String loginId) {
    if (loginId == null) {
      return;
    }
    ROLE_SET.invalidate("-" + ":" + loginId);
  }

  public static void bustAll() {
    ROLE_SET.invalidateAll();
  }

  @EventListener(ContextRefreshedEvent.class)
  public void onContextRefreshed() {
    bustAll();
  }
}
