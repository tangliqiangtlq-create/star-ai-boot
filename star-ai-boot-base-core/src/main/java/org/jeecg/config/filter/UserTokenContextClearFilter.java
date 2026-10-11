package org.jeecg.config.filter;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.jeecg.common.config.mqtoken.UserTokenContext;

/**
 * Clears UserTokenContext at request end to prevent ThreadLocal token reuse on pooled threads.
 *
 * <p>Uses try-finally so remove() runs on normal or exceptional completion, preventing: 1. internal tokens set for export/internal calls from being reused 2. iss
 * mismatch, invalid token, etc.
 */
public class UserTokenContextClearFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      chain.doFilter(request, response);
    } finally {
      UserTokenContext.remove();
    }
  }
}
