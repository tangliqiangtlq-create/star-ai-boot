package org.jeecg.config.filter;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Set {@code X-Content-Type-Options: nosniff} on all HTTP responses to reduce MIME sniffing treating
 * responses as executable scripts, mitigating JavaScript hijacking with JSON APIs (SECZ-SSCSP-CODE-021).
 */
public class ContentTypeOptionsFilter implements Filter {

  private static final String HEADER_NAME = "X-Content-Type-Options";
  private static final String HEADER_VALUE = "nosniff";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse) {
      ((HttpServletResponse) response).setHeader(HEADER_NAME, HEADER_VALUE);
    }
    chain.doFilter(request, response);
  }
}
