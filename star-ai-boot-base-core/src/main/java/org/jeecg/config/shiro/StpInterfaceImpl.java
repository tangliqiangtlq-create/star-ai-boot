package org.jeecg.config.shiro;

import cn.dev33.satoken.model.wrapperInfo.SaDisableWrapperInfo;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.RequestUtils;
import org.jeecg.common.util.security.InternalCallRequestSupport;
import org.jeecg.config.filter.InternalTrustedS2sFilter;
import org.springframework.stereotype.Component;

/**
 * Sa-Token permission/role source.
 *
 * <p>A5-1: DCS Feign calls carry {@code X-Internal-Call-Secret}. When that secret matches (or the
 * principal is {@code internal_call}), inject processengine add/sendEvent/view so {@code
 * @SaCheckPermission} passes even if (a) V1.0.87 grants are missing, (b) {@code queryUserAuths}
 * Feign falls back to null, or (c) elevation Cookie still carries the end-user token.
 *
 * <p>Paired with {@link InternalCallRequestSupport#ATTR_TRUSTED_INTERNAL_FEIGN} which is set by
 * {@link InternalTrustedS2sFilter} who also busts the Sa {@code Permission_List} cache before
 * the annotation check — otherwise a previously cached empty list would skip this class entirely.
 *
 * <p>CP-S2S-MIGR-33 Phase 1-2: Generalised S2S module matrix. {@link InternalTrustedS2sFilter}
 * marks the request with a MODULE_ID; we inject that module's minimum perms/role alongside the
 * user's DB grants.
 * <p>CP-S2S-MIGR-33 Phase 6 REMOVED legacy {@code InternalFeignProcessPermFilter} — its constant
 * ATTR_TRUSTED_INTERNAL_FEIGN moved to InternalCallRequestSupport and its filter logic is fully
 * subsumed by InternalTrustedS2sFilter generic matrix (PROCESSENGINE/add,sendEvent) + DCS_READ/WRITE modules.
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

  private static final String INTERNAL_CALL_USERNAME = "internal_call";
  public static final String INTERNAL_PROCESSENGINE_S2S_ROLE = "internal_processengine_s2s";
  public static final String INTERNAL_DCS_S2S_READ_ROLE = "internal_dcs_s2s_read";
  public static final String INTERNAL_DCS_S2S_WRITE_ROLE = "internal_dcs_s2s_write";
  public static final String INTERNAL_SYSTEM_S2S_ROLE = "internal_system_s2s";

  private static final List<String> INTERNAL_CALL_PROCESS_PERMS =
      List.of(
          "processengine:sys_process_instance:add",
          "processengine:sys_process_instance:sendEvent",
          "processengine:sys_process_instance:view",
          "processengine:remittance_process_instance:add",
          "processengine:remittance_process_instance:sendEvent");

  private static final List<String> DCS_S2S_READ_PERMS =
      List.of(
          "dcs:withdrawalRecord:view",
          "dcs:withdrawalRecord:export",
          "dcs:transferRecord:view",
          "dcs:transferRecord:export",
          "dcs:remittanceRecord:view",
          "dcs:remittanceRecord:export",
          "dcs:depositRecord:view",
          "dcs:account:view",
          "dcs:account:list",
          "dcs:user:view",
          "dcs:user:export",
          "dcs:agentIncomeRecord:view",
          "dcs:agentIncomeRecord:sum",
          "dcs:agentIncomeRecord:groupByTransactionType",
          "dcs:unifiedBizRecord:list",
          "dcs:currency:view",
          "dcs:currency:list",
          "dcs:bankAccount:view",
          "dcs:blockchain:view",
          "dcs:blockchainApiKey:view");

  private static final List<String> DCS_S2S_WRITE_PERMS =
      List.of(
          "dcs:withdrawalRecord:audit",
          "dcs:withdrawalRecord:edit",
          "dcs:withdrawalRecord:approve",
          "dcs:withdrawalRecord:reject",
          "dcs:transferRecord:audit",
          "dcs:transferRecord:edit",
          "dcs:transferRecord:approve",
          "dcs:remittanceRecord:audit",
          "dcs:remittanceRecord:edit",
          "dcs:remittanceRecord:sendEvent",
          "dcs:depositRecord:manualDeposit",
          "dcs:depositRecord:reconcile",
          "dcs:bankAccount:verify",
          "dcs:blockchainApiKey:add",
          "dcs:blockchainApiKey:edit",
          "dcs:blockchainApiKey:delete",
          "dcs:blockchain:triggerDepositScanJob",
          "dcs:user:openAccount",
          "dcs:accountConfiguration:edit");

  private static final List<String> SYSTEM_S2S_PERMS =
      List.of(
          "system:user:view",
          "system:user:list",
          "system:user:queryUserAuths",
          "system:user:translateDict",
          "system:dict:view",
          "system:dict:list",
          "system:dict:queryDictItems",
          "system:ossFile:view",
          "system:ossFile:upload",
          "system:depart:view",
          "system:role:view");

  private static final Map<String, S2sModulePerms> S2S_PERMISSION_MATRIX;

  private static final List<String> PUBLIC_FORBIDDEN_PREFIXES =
      List.of("/login", "/register", "/common/file");

  static {
    Map<String, S2sModulePerms> m = new LinkedHashMap<>();
    m.put(
        InternalTrustedS2sFilter.MODULE_PROCESSENGINE,
        new S2sModulePerms(INTERNAL_PROCESSENGINE_S2S_ROLE, INTERNAL_CALL_PROCESS_PERMS));
    m.put(
        InternalTrustedS2sFilter.MODULE_DCS_S2S_READ,
        new S2sModulePerms(INTERNAL_DCS_S2S_READ_ROLE, DCS_S2S_READ_PERMS));
    m.put(
        InternalTrustedS2sFilter.MODULE_DCS_S2S_WRITE,
        new S2sModulePerms(INTERNAL_DCS_S2S_WRITE_ROLE, DCS_S2S_WRITE_PERMS));
    m.put(
        InternalTrustedS2sFilter.MODULE_SYSTEM_S2S,
        new S2sModulePerms(INTERNAL_SYSTEM_S2S_ROLE, SYSTEM_S2S_PERMS));
    S2S_PERMISSION_MATRIX = Collections.unmodifiableMap(m);
  }

  public static Map<String, S2sModulePerms> getPermissionMatrixSnapshot() {
    return S2S_PERMISSION_MATRIX;
  }

  @Resource private CommonAPI commonAPI;

  /**
   * Reentrancy guard: C4 / dict whitelist paths may call {@code StpUtil.hasRole} while roles are
   * still loading; that must not re-enter {@link #getRoleList} via {@code resolveUsername}.
   */
  private static final ThreadLocal<Boolean> LOADING_ROLES = new ThreadLocal<>();

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    if (org.jeecg.common.config.TenantContext.getTenant() == null
        || org.jeecg.common.config.TenantContext.getTenant().isBlank()) {
      org.jeecg.common.config.TenantContext.setTenant(
          String.valueOf(org.jeecg.common.constant.CommonConstant.TENANT_ID_DEFAULT_VALUE));
    }
    LinkedHashSet<String> auths = new LinkedHashSet<>();
    try {
      Set<String> fromApi = commonAPI.queryUserAuths(String.valueOf(loginId));
      if (fromApi != null) {
        auths.addAll(fromApi);
      }
    } catch (Exception ex) {
      log.warn("queryUserAuths failed loginId={}: {}", loginId, ex.toString());
    }

    S2sModulePerms modulePerms = grantS2sPermsByModule();
    if (modulePerms != null && modulePerms.permissions != null) {
      auths.addAll(modulePerms.permissions);
      log.info(
          "Injected S2S module perms for loginId={} size={} role={}",
          loginId,
          modulePerms.permissions.size(),
          modulePerms.roleId);
    }

    if (grantProcessEngineServicePerms(loginId)) {
      auths.addAll(INTERNAL_CALL_PROCESS_PERMS);
      log.info(
          "Injected processengine service perms for loginId={} size={}",
          loginId,
          INTERNAL_CALL_PROCESS_PERMS.size());
    }

    if (isInternalCallUser(loginId) && modulePerms == null) {
      HttpServletRequest req = RequestUtils.getCurrentRequest();
      if (req != null) {
        String uri = req.getRequestURI();
        boolean processEngineUri =
            uri != null
                && (uri.contains("/processengine/") || uri.contains("/processInstance/"));
        if (!processEngineUri) {
          log.warn(
              "Cross-module block: internal_call user loginId={} uri={}",
              loginId,
              uri);
          auths.clear();
        }
      }
    }
    return new ArrayList<>(auths);
  }

  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    if (Boolean.TRUE.equals(LOADING_ROLES.get())) {
      return Collections.emptyList();
    }
    LOADING_ROLES.set(Boolean.TRUE);
    try {
      // Login bootstrap: Feign/self queryUserRoles hits C4 if ThreadLocal tenant unset.
      if (org.jeecg.common.config.TenantContext.getTenant() == null
          || org.jeecg.common.config.TenantContext.getTenant().isBlank()) {
        org.jeecg.common.config.TenantContext.setTenant(
            String.valueOf(org.jeecg.common.constant.CommonConstant.TENANT_ID_DEFAULT_VALUE));
      }
      LinkedHashSet<String> roles = new LinkedHashSet<>();

      S2sModulePerms modulePerms = grantS2sPermsByModule();
      if (modulePerms != null && StrUtil.isNotBlank(modulePerms.roleId)) {
        roles.add(modulePerms.roleId);
      }

      if (grantProcessEngineServicePerms(loginId)) {
        roles.add(INTERNAL_PROCESSENGINE_S2S_ROLE);
      }
      // Prefer by-id lookup — resolveUsername→translateDictFromTable re-enters hasRole/getRoleList.
      try {
        Set<String> fromApi = commonAPI.queryUserRolesById(String.valueOf(loginId));
        if (fromApi != null) {
          roles.addAll(fromApi);
        }
      } catch (Exception ex) {
        log.warn("queryUserRolesById failed loginId={}: {}", loginId, ex.toString());
      }
      return new ArrayList<>(roles);
    } finally {
      LOADING_ROLES.remove();
    }
  }

  @Override
  public SaDisableWrapperInfo isDisabled(Object loginId, String service) {
    try {
      String username = resolveUsername(loginId);
      if (StrUtil.isBlank(username)) {
        return new SaDisableWrapperInfo(false, 0, 1);
      }
      LoginUser loginUser = commonAPI.getUserByName(username);
      if (loginUser == null || loginUser.getStatus() == null) {
        return new SaDisableWrapperInfo(false, 0, 1);
      }
      return new SaDisableWrapperInfo(
          loginUser.getStatus() == 2, loginUser.getStatus() == 2 ? -1 : 0, 1);
    } catch (Exception ex) {
      log.warn("isDisabled check failed loginId={}: {}", loginId, ex.toString());
      return new SaDisableWrapperInfo(false, 0, 1);
    }
  }

  private boolean grantProcessEngineServicePerms(Object loginId) {
    // S6-11: authorization must come from a VERIFIED service context, never from the username.
    // The previous `isInternalCallUser(loginId) → true` branch granted processengine
    // add/sendEvent/view perms to any session merely named "internal_call" (forgeable, and
    // exploitable straight away if the S2S boundary is broken — see V2.8/S6-1).
    HttpServletRequest req = RequestUtils.getCurrentRequest();
    if (req == null) {
      return false;
    }
    if (Boolean.TRUE.equals(
        req.getAttribute(InternalCallRequestSupport.ATTR_TRUSTED_INTERNAL_FEIGN))) {
      return true;
    }
    String uri = req.getRequestURI();
    if (uri == null
        || (!uri.contains("/processengine/")
            && !uri.contains("/processInstance/"))) {
      return false;
    }
    return InternalCallRequestSupport.secretMatches(req);
  }

  private S2sModulePerms grantS2sPermsByModule() {
    HttpServletRequest req = RequestUtils.getCurrentRequest();
    if (req == null) {
      return null;
    }
    Object attr = req.getAttribute(InternalTrustedS2sFilter.ATTR_TRUSTED_S2S_MODULE);
    if (!(attr instanceof String)) {
      return null;
    }
    String moduleId = (String) attr;
    S2sModulePerms perms = S2S_PERMISSION_MATRIX.get(moduleId);
    if (perms == null) {
      log.warn("S2S module {} not found in permission matrix", moduleId);
      return null;
    }
    return perms;
  }

  @PostConstruct
  public void validateS2sMatrix() {
    log.info("[S2S-MIGR-33] Boot-time: S2S permission matrix integrity check");

    for (Map.Entry<String, S2sModulePerms> e : S2S_PERMISSION_MATRIX.entrySet()) {
      String moduleId = e.getKey();
      S2sModulePerms def = e.getValue();
      if (def.permissions == null || def.permissions.isEmpty()) {
        throw new IllegalStateException(
            "[S2S-MIGR-33-BROKEN] S2S module " + moduleId + " has EMPTY permissions (size >= 1 required).");
      }
      if (StrUtil.isBlank(def.roleId)) {
        throw new IllegalStateException(
            "[S2S-MIGR-33-BROKEN] S2S module " + moduleId + " has blank roleId.");
      }
    }

    List<String> pePerms = INTERNAL_CALL_PROCESS_PERMS;
    S2sModulePerms peDef = S2S_PERMISSION_MATRIX.get(InternalTrustedS2sFilter.MODULE_PROCESSENGINE);
    if (peDef == null) {
      throw new IllegalStateException(
          "[S2S-MIGR-33-BROKEN] PROCESSENGINE module MISSING from S2S_PERMISSION_MATRIX.");
    }
    if (peDef.permissions.size() != 5) {
      throw new IllegalStateException(
          "[S2S-MIGR-33-BROKEN] PROCESSENGINE perms size="
              + peDef.permissions.size()
              + ", expected 5.");
    }
    if (!new HashSet<>(peDef.permissions).equals(new HashSet<>(pePerms))) {
      throw new IllegalStateException(
          "[S2S-MIGR-33-BROKEN] PROCESSENGINE perms not byte-equal to INTERNAL_CALL_PROCESS_PERMS constant.");
    }

    Map<String, InternalTrustedS2sFilter.S2sModuleDef> filterMatrix =
        InternalTrustedS2sFilter.getS2sMatrixSnapshot();
    Set<String> stpIds = S2S_PERMISSION_MATRIX.keySet();
    Set<String> filterIds = filterMatrix.keySet();
    if (!stpIds.equals(filterIds)) {
      throw new IllegalStateException(
          "[S2S-MIGR-33-BROKEN] S2S module id mismatch between StpInterfaceImpl ("
              + stpIds
              + ") and InternalTrustedS2sFilter ("
              + filterIds
              + "). They must be identical.");
    }

    // Gate: resolveModuleId must be unambiguous. Shared URI prefixes (e.g. DCS READ/WRITE
    // both use /dcs/) are allowed ONLY when methodFilters are both non-empty and disjoint.
    Set<String> allPrefixes = new HashSet<>();
    List<Map.Entry<String, InternalTrustedS2sFilter.S2sModuleDef>> moduleEntries =
        new ArrayList<>(filterMatrix.entrySet());
    for (int i = 0; i < moduleEntries.size(); i++) {
      InternalTrustedS2sFilter.S2sModuleDef a = moduleEntries.get(i).getValue();
      allPrefixes.addAll(a.uriPrefixes);
      for (int j = i + 1; j < moduleEntries.size(); j++) {
        InternalTrustedS2sFilter.S2sModuleDef b = moduleEntries.get(j).getValue();
        if (!uriPrefixesOverlap(a.uriPrefixes, b.uriPrefixes)) {
          continue;
        }
        boolean aUnlimited = a.methodFilter == null || a.methodFilter.isEmpty();
        boolean bUnlimited = b.methodFilter == null || b.methodFilter.isEmpty();
        if (aUnlimited || bUnlimited) {
          throw new IllegalStateException(
              "[S2S-MIGR-33-BROKEN] S2S uri prefix overlap without method disambiguation: '"
                  + moduleEntries.get(i).getKey()
                  + "' vs '"
                  + moduleEntries.get(j).getKey()
                  + "'. Overlapping prefixes require non-empty pairwise-disjoint methodFilters.");
        }
        Set<org.springframework.http.HttpMethod> intersect = new HashSet<>(a.methodFilter);
        intersect.retainAll(b.methodFilter);
        if (!intersect.isEmpty()) {
          throw new IllegalStateException(
              "[S2S-MIGR-33-BROKEN] S2S uri prefix overlap with ambiguous methods "
                  + intersect
                  + ": '"
                  + moduleEntries.get(i).getKey()
                  + "' vs '"
                  + moduleEntries.get(j).getKey()
                  + "'. methodFilters must be pairwise disjoint.");
        }
      }
    }

    Set<String> excludeUris = InternalTrustedS2sFilter.getSystemS2sExcludeUris();
    InternalTrustedS2sFilter.S2sModuleDef systemDef =
        filterMatrix.get(InternalTrustedS2sFilter.MODULE_SYSTEM_S2S);
    if (systemDef != null) {
      for (String excl : excludeUris) {
        for (String prefix : systemDef.uriPrefixes) {
          if (excl.startsWith(prefix) && !InternalTrustedS2sFilter.matchesSystemExcludeUris(excl)) {
            throw new IllegalStateException(
                "[S2S-MIGR-33-BROKEN] SYSTEM_S2S excludeUri '"
                    + excl
                    + "' falls within prefix '"
                    + prefix
                    + "' but matchesSystemExcludeUris returned false.");
          }
        }
      }
    }

    for (String prefix : allPrefixes) {
      for (String forbidden : PUBLIC_FORBIDDEN_PREFIXES) {
        String stripped = prefix;
        if (stripped.startsWith("/jeecg-")) {
          int idx = stripped.indexOf('/', 1);
          if (idx > 0) {
            stripped = stripped.substring(idx);
          }
        }
        if (stripped.startsWith(forbidden)) {
          throw new IllegalStateException(
              "[S2S-MIGR-33-BROKEN] S2S uri prefix '"
                  + prefix
                  + "' overlaps with forbidden public prefix '"
                  + forbidden
                  + "' (C-4 violated).");
        }
      }
    }

    log.info(
        "[S2S-MIGR-33] PASS: S2S matrix integrity OK — modules={}, PROCESSENGINE size=5, prefix/method unambiguous, no public-endpoint overlap.",
        stpIds.size());
  }

  private static boolean uriPrefixesOverlap(List<String> a, List<String> b) {
    if (a == null || b == null) {
      return false;
    }
    for (String pa : a) {
      for (String pb : b) {
        if (pa.startsWith(pb) || pb.startsWith(pa)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isInternalCallUser(Object loginId) {
    LoginUser fromSession = sessionLoginUser(loginId);
    if (fromSession != null && StrUtil.isNotBlank(fromSession.getUsername())) {
      return INTERNAL_CALL_USERNAME.equalsIgnoreCase(fromSession.getUsername());
    }
    // No session yet (login bootstrap / nested StpUtil.hasRole): skip DB username resolve —
    // translateDictFromTable can re-enter getRoleList and StackOverflow.
    if (Boolean.TRUE.equals(LOADING_ROLES.get())) {
      return false;
    }
    String username = resolveUsername(loginId);
    return INTERNAL_CALL_USERNAME.equalsIgnoreCase(username);
  }

  private LoginUser sessionLoginUser(Object loginId) {
    if (loginId == null) {
      return null;
    }
    try {
      Object userObj = StpUtil.getSessionByLoginId(loginId).get("loginUser");
      if (userObj instanceof LoginUser) {
        return (LoginUser) userObj;
      }
    } catch (Exception ignore) {
    }
    try {
      if (StpUtil.isLogin() && String.valueOf(loginId).equals(StpUtil.getLoginIdAsString())) {
        Object userObj = StpUtil.getSession().get("loginUser");
        if (userObj instanceof LoginUser) {
          return (LoginUser) userObj;
        }
      }
    } catch (Exception ignore) {
    }
    return null;
  }

  private String resolveUsername(Object loginId) {
    LoginUser fromSession = sessionLoginUser(loginId);
    if (fromSession != null && StrUtil.isNotBlank(fromSession.getUsername())) {
      return fromSession.getUsername();
    }
    if (loginId == null) {
      return null;
    }
    try {
      String username =
          commonAPI.translateDictFromTable("sys_user", "username", "id", String.valueOf(loginId));
      return StrUtil.isBlank(username) ? null : username;
    } catch (Exception ex) {
      log.warn("resolveUsername Feign failed loginId={}: {}", loginId, ex.toString());
      return null;
    }
  }

  public static class S2sModulePerms {
    public final String roleId;
    public final List<String> permissions;

    public S2sModulePerms(String roleId, List<String> permissions) {
      this.roleId = roleId;
      this.permissions =
          permissions == null ? Collections.emptyList() : Collections.unmodifiableList(permissions);
    }
  }
}
