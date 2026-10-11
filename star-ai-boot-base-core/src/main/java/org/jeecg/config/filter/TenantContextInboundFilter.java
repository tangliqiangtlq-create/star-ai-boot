package org.jeecg.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.constant.CommonConstant;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bind inbound {@code X-Tenant-Id} into {@link TenantContext} so Feign self-calls and C4
 * tenant-line queries see the same tenant as the caller thread that set ThreadLocal.
 */
public class TenantContextInboundFilter extends OncePerRequestFilter implements Ordered {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String tenantId = request.getHeader(CommonConstant.TENANT_ID);
    if (!StringUtils.hasText(tenantId)) {
      tenantId = request.getParameter(CommonConstant.TENANT_ID);
    }
    if (!StringUtils.hasText(tenantId)) {
      tenantId = request.getParameter("tenantId");
    }
    boolean bound = false;
    if (StringUtils.hasText(tenantId)) {
      TenantContext.setTenant(tenantId.trim());
      bound = true;
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (bound) {
        TenantContext.clear();
      }
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 20;
  }
}
