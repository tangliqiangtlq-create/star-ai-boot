package org.jeecg.config.filter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.security.InternalCallRequestSupport;
import org.springframework.http.HttpMethod;

/**
 * CP-S2S-MIGR-33-v2: Generic module-matrix based S2S trusted-call filter.
 *
 * <p>When the inbound request carries a valid {@code X-Internal-Call-Secret} AND originates from a
 * trusted private CIDR, AND the URI matches a registered S2S module prefix (optionally gated by
 * HTTP method), it:
 *
 * <ol>
 *   <li>Marks the request with {@link #ATTR_TRUSTED_S2S_MODULE} so {@code StpInterfaceImpl} can
 *       inject minimum-required per-module permissions.
 *   <li>Also sets {@link InternalCallRequestSupport#ATTR_TRUSTED_INTERNAL_FEIGN} so legacy
 *       listeners / ProcessInstanceController helpers see the same trusted marker.
 *   <li>Busts the Sa-Token {@code Permission_List} / {@code Role_List} caches on the current
 *       session so {@code StpInterfaceImpl} is re-entered.
 * </ol>
 *
 * <p>Fail-close: any missing check (CIDR / secret / URI match / method filter) means the
 * attribute is NOT set.
 *
 * <p>Registered in {@code WebMvcConfiguration} at order {@code HIGHEST_PRECEDENCE + 1} — strictly
 * AFTER {@link InternalSecretHeaderStripFilter} (HP) and strictly BEFORE
 * {@code GatewaySignatureFilter} (HP+3).
 */
@Slf4j
public class InternalTrustedS2sFilter implements Filter {

  public static final String ATTR_TRUSTED_S2S_MODULE = "__jeecg_trusted_s2s_module";
  public static final String MODULE_PROCESSENGINE = "PROCESSENGINE";
  public static final String MODULE_DCS_S2S_READ = "DCS_S2S_READ";
  public static final String MODULE_DCS_S2S_WRITE = "DCS_S2S_WRITE";
  public static final String MODULE_SYSTEM_S2S = "SYSTEM_S2S";

  private static final Set<String> SYSTEM_S2S_EXCLUDE_URIS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  // R-06 / C-4: never inject SYSTEM_S2S on token-mint or public anon paths
                  "/sys/innerLogin",
                  "/sys/innerTestLogin",
                  "/sys/common/file",
                  "/sys/getEncryptedString",
                  "/sys/login",
                  "/sys/recoverPassword",
                  "/sys/register",
                  "/sys/randomImage",
                  "/sys/checkCaptcha",
                  "/sys/smsCheckCaptcha",
                  "/sys/thirdLogin",
                  "/sys/sendVerificationCode",
                  "/sys/phoneLogin")));

  private static final Map<String, S2sModuleDef> S2S_MATRIX;

  static {
    Map<String, S2sModuleDef> m = new LinkedHashMap<>();

    m.put(
        MODULE_PROCESSENGINE,
        new S2sModuleDef(
            Arrays.asList(
                "/processengine/",
                "/jeecg-processengine/processengine/",
                "/processInstance/",
                "/jeecg-processengine/processInstance/"),
            Collections.emptySet()));

    // READ/WRITE share /dcs/ prefixes; disambiguate by method (must be pairwise disjoint —
    // otherwise resolveModuleId first-match would permanently shadow WRITE on POST).
    m.put(
        MODULE_DCS_S2S_READ,
        new S2sModuleDef(
            Arrays.asList("/dcs/", "/jeecg-dcs/dcs/"),
            new HashSet<>(Collections.singletonList(HttpMethod.GET))));

    m.put(
        MODULE_DCS_S2S_WRITE,
        new S2sModuleDef(
            Arrays.asList("/dcs/", "/jeecg-dcs/dcs/"),
            new HashSet<>(
                Arrays.asList(HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE))));

    m.put(
        MODULE_SYSTEM_S2S,
        new S2sModuleDef(
            Arrays.asList("/sys/", "/jeecg-system/sys/"), Collections.emptySet()));

    S2S_MATRIX = Collections.unmodifiableMap(m);
  }

  public static Map<String, S2sModuleDef> getS2sMatrixSnapshot() {
    return S2S_MATRIX;
  }

  public static Set<String> getSystemS2sExcludeUris() {
    return SYSTEM_S2S_EXCLUDE_URIS;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest)) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;

    String uri = req.getRequestURI();
    HttpMethod method = safeHttpMethod(req.getMethod());
    boolean matrixCandidate =
        !matchesSystemExcludeUris(uri) && resolveModuleId(uri, method) != null;

    if (!InternalCallRequestSupport.isTrustedInboundRemoteAddr(req)) {
      if (matrixCandidate) {
        log.warn(
            "S2S mark skipped: CIDR reject uri={} method={} remote={}",
            uri,
            method,
            req.getRemoteAddr());
      }
      chain.doFilter(request, response);
      return;
    }
    if (!secretMatches(req)) {
      if (matrixCandidate) {
        log.warn(
            "S2S mark skipped: secret mismatch/blank uri={} method={} remote={} hasHeader={}",
            uri,
            method,
            req.getRemoteAddr(),
            StrUtil.isNotBlank(
                req.getHeader(org.jeecg.config.filter.InternalSecretHeaderStripFilter.HEADER_NAME)));
      }
      chain.doFilter(request, response);
      return;
    }

    if (matchesSystemExcludeUris(uri)) {
      chain.doFilter(request, response);
      return;
    }
    String moduleId = resolveModuleId(uri, method);
    if (moduleId == null) {
      chain.doFilter(request, response);
      return;
    }

    req.setAttribute(ATTR_TRUSTED_S2S_MODULE, moduleId);
    req.setAttribute(InternalCallRequestSupport.ATTR_TRUSTED_INTERNAL_FEIGN, Boolean.TRUE);
    // Mode B: Feign is secret-only (no Cookie). Controllers still use @SaCheckLogin /
    // @SaCheckPermission + StpUtil.getLoginId — establish a short-lived request principal
    // after CIDR+secret+matrix already passed (not Mode A innerLogin minting).
    // May no-op if SaTokenContext not ready yet; SaInterceptor re-ensures before annotations.
    ensureEphemeralS2sSession(moduleId);
    log.info(
        "Trusted S2S matched: module={} uri={} method={} loginId={}",
        moduleId,
        uri,
        method,
        safeLoginId());

    chain.doFilter(request, response);
  }

  /**
   * Bind a transient Sa principal for this inbound S2S request so annotation checks and
   * {@code StpInterfaceImpl} permission injection have a loginId. No lasting cookie / response
   * token is required for Feign callers.
   *
   * <p>Safe to call from Filter and from {@code SaInterceptor} (Sa context is ready in the
   * interceptor). Idempotent when already logged in.
   */
  public static void ensureEphemeralS2sSession(String moduleId) {
    if (StrUtil.isBlank(moduleId)) {
      return;
    }
    try {
      if (StpUtil.isLogin()) {
        return;
      }
      LoginUser cached = S2sSessionCache.get(moduleId);
      String loginId = "s2s:" + moduleId;
      if (cached != null) {
        try {
          StpUtil.getSession().set("loginUser", cached);
        } catch (Exception ignore) {
        }
        return;
      }
      StpUtil.login(loginId);
      LoginUser lu = new LoginUser();
      lu.setId(loginId);
      lu.setUsername("internal_" + moduleId.toLowerCase());
      try {
        StpUtil.getSession().set("loginUser", lu);
      } catch (Exception ignore) {
      }
      S2sSessionCache.put(moduleId, lu);
    } catch (Exception ex) {
      log.warn("S2S ephemeral login failed module={}: {}", moduleId, ex.toString());
    }
  }

  public static boolean matchesSystemExcludeUris(String uri) {
    if (uri == null) {
      return false;
    }
    String suffix = stripGatewayPrefix(uri);
    for (String excl : SYSTEM_S2S_EXCLUDE_URIS) {
      if (suffix.startsWith(excl)) {
        return true;
      }
    }
    return false;
  }

  public static String resolveModuleId(String uri, HttpMethod method) {
    if (uri == null) {
      return null;
    }
    String suffix = stripGatewayPrefix(uri);
    for (Map.Entry<String, S2sModuleDef> e : S2S_MATRIX.entrySet()) {
      S2sModuleDef def = e.getValue();
      if (!matchesAnyPrefix(suffix, def.uriPrefixes)) {
        continue;
      }
      if (def.methodFilter != null && !def.methodFilter.isEmpty()) {
        if (method == null || !def.methodFilter.contains(method)) {
          continue;
        }
      }
      return e.getKey();
    }
    return null;
  }

  private static String stripGatewayPrefix(String uri) {
    if (uri == null) {
      return null;
    }
    if (uri.startsWith("/jeecg-dcs/")) {
      return uri.substring("/jeecg-dcs".length());
    }
    if (uri.startsWith("/jeecg-system/")) {
      return uri.substring("/jeecg-system".length());
    }
    if (uri.startsWith("/jeecg-processengine/")) {
      return uri.substring("/jeecg-processengine".length());
    }
    if (uri.startsWith("/jeecg-gateway/")) {
      return uri.substring("/jeecg-gateway".length());
    }
    return uri;
  }

  private static boolean matchesAnyPrefix(String suffix, List<String> prefixes) {
    if (suffix == null || prefixes == null) {
      return false;
    }
    for (String p : prefixes) {
      if (suffix.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  private static HttpMethod safeHttpMethod(String method) {
    if (StrUtil.isBlank(method)) {
      return null;
    }
    try {
      return HttpMethod.valueOf(method.toUpperCase());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private boolean secretMatches(HttpServletRequest req) {
    return InternalCallRequestSupport.secretMatches(req);
  }

  private static void bustSaAuthCache() {
    try {
      if (!StpUtil.isLogin()) {
        return;
      }
      Object loginId = StpUtil.getLoginId();
      try {
        cn.dev33.satoken.session.SaSession tokenSession = StpUtil.getTokenSession();
        if (tokenSession != null) {
          tokenSession.delete("Permission_List");
          tokenSession.delete("Role_List");
        }
      } catch (Exception ignore) {
      }
      try {
        cn.dev33.satoken.session.SaSession acc = StpUtil.getSessionByLoginId(loginId);
        if (acc != null) {
          acc.delete("Permission_List");
          acc.delete("Role_List");
        }
      } catch (Exception ignore) {
      }
    } catch (Exception ignore) {
    }
  }

  private static String safeLoginId() {
    try {
      return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "-";
    } catch (Exception e) {
      return "-";
    }
  }

  public static class S2sModuleDef {
    public final List<String> uriPrefixes;
    public final Set<HttpMethod> methodFilter;

    public S2sModuleDef(List<String> uriPrefixes, Set<HttpMethod> methodFilter) {
      this.uriPrefixes = Collections.unmodifiableList(uriPrefixes);
      this.methodFilter =
          methodFilter == null
              ? Collections.emptySet()
              : Collections.unmodifiableSet(new HashSet<>(methodFilter));
    }
  }
}
