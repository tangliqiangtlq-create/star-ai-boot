package org.jeecg.config.firewall.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.base.controller.JeecgController;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class PageSizeCapRequestFilter extends OncePerRequestFilter {

  public static final int MAX_PAGE_SIZE = JeecgController.MAX_PAGE_SIZE;
  public static final int DEFAULT_PAGE_SIZE = 10;
  private static final Set<String> SIZE_PARAM_NAMES = Set.of(
      "pageSize", "page_size", "page_size_limit", "size", "limit", "rows", "per_page", "perPage");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    boolean mutated = false;
    Map<String, String[]> paramMap = new LinkedHashMap<>();
    Enumeration<String> names = request.getParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      paramMap.put(name, request.getParameterValues(name));
    }

    for (String paramName : SIZE_PARAM_NAMES) {
      String[] values = paramMap.get(paramName);
      if (values == null || values.length == 0) continue;
      for (int i = 0; i < values.length; i++) {
        String raw = values[i];
        if (raw == null || raw.isEmpty()) continue;
        try {
          long v = Long.parseLong(raw);
          if (v > MAX_PAGE_SIZE) {
            log.warn(
                "[PAGE-SIZE-CAP] uri={} param={} rawValue={} exceeds MAX={}, capped to {}",
                request.getRequestURI(), paramName, v, MAX_PAGE_SIZE, MAX_PAGE_SIZE);
            values[i] = String.valueOf(MAX_PAGE_SIZE);
            mutated = true;
          } else if (v <= 0) {
            values[i] = String.valueOf(DEFAULT_PAGE_SIZE);
            mutated = true;
          }
        } catch (NumberFormatException ignore) {
        }
      }
      paramMap.put(paramName, values);
    }

    if (!mutated) {
      filterChain.doFilter(request, response);
      return;
    }

    final Map<String, String[]> finalParams =
        Collections.unmodifiableMap(new TreeMap<>(new HashMap<>(paramMap)));
    HttpServletRequestWrapper wrapped =
        new HttpServletRequestWrapper(request) {
          @Override
          public Map<String, String[]> getParameterMap() {
            return finalParams;
          }

          @Override
          public String getParameter(String name) {
            String[] values = finalParams.get(name);
            return values == null || values.length == 0 ? null : values[0];
          }

          @Override
          public Enumeration<String> getParameterNames() {
            return Collections.enumeration(new HashSet<>(finalParams.keySet()));
          }

          @Override
          public String[] getParameterValues(String name) {
            return finalParams.get(name);
          }
        };
    filterChain.doFilter(wrapped, response);
  }
}
