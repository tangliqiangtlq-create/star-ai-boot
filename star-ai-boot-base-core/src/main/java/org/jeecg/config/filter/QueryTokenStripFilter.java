package org.jeecg.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryTokenStripFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(QueryTokenStripFilter.class);

  private static final Set<String> TOKEN_PARAM_NAMES;

  private static final Set<String> STRIP_AUDIT_SENSITIVE_HEADERS;

  static {
    Set<String> s = new HashSet<>();
    s.add("token");
    s.add("satoken");
    s.add("loginToken");
    s.add("access_token");
    s.add("X-Access-Token".toLowerCase());
    TOKEN_PARAM_NAMES = Collections.unmodifiableSet(s);

    Set<String> h = new HashSet<>();
    h.add("user-agent");
    h.add("referer");
    h.add("origin");
    h.add("x-forwarded-for");
    h.add("x-real-ip");
    STRIP_AUDIT_SENSITIVE_HEADERS = Collections.unmodifiableSet(h);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest)) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;
    Set<String> hitParams = null;
    for (String name : TOKEN_PARAM_NAMES) {
      if (req.getParameter(name) != null) {
        if (hitParams == null) hitParams = new HashSet<>(4);
        hitParams.add(name);
      }
    }
    if (hitParams == null || hitParams.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    StringBuilder headersSb = new StringBuilder(128);
    for (String h : STRIP_AUDIT_SENSITIVE_HEADERS) {
      String v = req.getHeader(h);
      if (v != null && !v.isEmpty()) {
        if (headersSb.length() > 0) headersSb.append(';');
        headersSb.append(h).append('=').append(truncate(v, 200));
      }
    }
    String maskedQs = maskQueryString(req.getQueryString());
    String method = req.getMethod();
    String remoteAddr = req.getRemoteAddr();
    String forwarded = req.getHeader("X-Forwarded-For");
    String realIp = req.getHeader("X-Real-IP");
    String reqUri = req.getRequestURI();
    long contentLen = req.getContentLengthLong();
    log.warn(
        "SEC-B2-STRIP_TOKEN_QUERY method={} uri={} remoteAddr={} xff={} realIp={} hitParams={} "
            + "maskedQuery={} contentLenBytes={} headers=[{}]",
        method,
        reqUri,
        remoteAddr,
        truncate(forwarded, 120),
        truncate(realIp, 60),
        String.join(",", hitParams),
        maskedQs,
        contentLen,
        headersSb);
    chain.doFilter(new StripTokenRequestWrapper(req), response);
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) return "";
    return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...(trunc)";
  }

  private static String maskQueryString(String original) {
    if (original == null || original.isEmpty()) return "";
    String[] pairs = original.split("&");
    StringBuilder sb = new StringBuilder(original.length());
    for (String pair : pairs) {
      if (pair.isEmpty()) continue;
      int eq = pair.indexOf('=');
      String key = eq < 0 ? pair : pair.substring(0, eq);
      String decodedKey = key;
      try {
        decodedKey = java.net.URLDecoder.decode(key, "UTF-8");
      } catch (Exception ignored) {
      }
      boolean isToken =
          TOKEN_PARAM_NAMES.contains(key.toLowerCase())
              || TOKEN_PARAM_NAMES.contains(decodedKey.toLowerCase());
      if (sb.length() > 0) sb.append('&');
      sb.append(key).append(isToken ? "=<REDACTED>" : (eq < 0 ? "" : "=<HIDE>"));
    }
    return truncate(sb.toString(), 300);
  }

  private static class StripTokenRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String[]> filteredParams;

    StripTokenRequestWrapper(HttpServletRequest req) {
      super(req);
      Map<String, String[]> original = new TreeMap<>(req.getParameterMap());
      Map<String, String[]> filtered = new HashMap<>(original.size());
      for (Map.Entry<String, String[]> e : original.entrySet()) {
        String key = e.getKey();
        if (key != null && TOKEN_PARAM_NAMES.contains(key.toLowerCase())) {
          continue;
        }
        filtered.put(key, e.getValue());
      }
      this.filteredParams = Collections.unmodifiableMap(filtered);
    }

    @Override
    public String getParameter(String name) {
      if (name != null && TOKEN_PARAM_NAMES.contains(name.toLowerCase())) {
        return null;
      }
      String[] values = filteredParams.get(name);
      return values == null || values.length == 0 ? null : values[0];
    }

    @Override
    public Map<String, String[]> getParameterMap() {
      return filteredParams;
    }

    @Override
    public Enumeration<String> getParameterNames() {
      return Collections.enumeration(filteredParams.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
      if (name != null && TOKEN_PARAM_NAMES.contains(name.toLowerCase())) {
        return null;
      }
      return filteredParams.get(name);
    }

    @Override
    public String getQueryString() {
      String original = super.getQueryString();
      if (original == null || original.isEmpty()) {
        return original;
      }
      StringBuilder sb = new StringBuilder();
      String[] pairs = original.split("&");
      for (String pair : pairs) {
        if (pair.isEmpty()) continue;
        int eq = pair.indexOf('=');
        String key = eq < 0 ? pair : pair.substring(0, eq);
        try {
          String decoded = java.net.URLDecoder.decode(key, "UTF-8");
          if (TOKEN_PARAM_NAMES.contains(decoded.toLowerCase())) {
            continue;
          }
        } catch (Exception ignored) {
        }
        if (TOKEN_PARAM_NAMES.contains(key.toLowerCase())) {
          continue;
        }
        if (sb.length() > 0) sb.append('&');
        sb.append(pair);
      }
      return sb.length() == 0 ? null : sb.toString();
    }
  }
}
