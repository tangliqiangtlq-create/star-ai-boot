package org.jeecg.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Locale;
import org.jeecg.common.util.ApiInputSanitizer;

/**
 * Coarse XSS check on URL query params and form fields; blocks obvious malicious payloads before Controller (SECZ-SSCSP-CODE-003).
 */
public class ApiInputXssFilter implements Filter {

  private static final String[] SKIP_PATH_PREFIXES = {
    "/sys/common/static/",
    "/sys/common/pdf/",
    // V2.15-11: removed the dead uqpay webhook prefix — no such controller exists.
    "/actuator/",
    "/webjars/",
    "/doc.html"
  };

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest httpRequest) {
      String uri = httpRequest.getRequestURI();
      if (uri != null && !shouldSkip(uri)) {
        for (String name : httpRequest.getParameterMap().keySet()) {
          String[] values = httpRequest.getParameterValues(name);
          if (values == null) {
            continue;
          }
          for (String value : values) {
            ApiInputSanitizer.assertNoXssInQueryParam(name, value);
          }
        }
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean shouldSkip(String uri) {
    String path = uri.toLowerCase(Locale.ROOT);
    for (String prefix : SKIP_PATH_PREFIXES) {
      if (path.contains(prefix)) {
        return true;
      }
    }
    return false;
  }
}
