package org.jeecg.config.firewall.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.I18nUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Low-code mode (dev: development; prod: production — disables all online development configuration)
 *
 * <p>When prod is enabled, the following are disabled; only functional testing remains (admin role retains config access):
 * 1. All online form configuration, code generation, and table import
 * 2. All online report configuration and SQL parsing
 * 3. All online chart configuration and SQL parsing
 * 4. Dashboard online configuration and SQL parsing
 * 5. Big-screen online configuration and SQL parsing
 *
 * <p>Jimureport handled separately: 1. Jimureport online configuration and SQL parsing
 *
 * @author qinfeng
 * @date 20230904
 */
@Slf4j
public class LowCodeModeInterceptor implements HandlerInterceptor {
  /** Low-code development mode */
  public static final String LOW_CODE_MODE_DEV = "dev";

  public static final String LOW_CODE_MODE_PROD = "prod";

  @Resource private JeecgBaseConfig jeecgBaseConfig;
  @Autowired private CommonAPI commonAPI;

  /** Invoked before request processing */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // 1. Verify low-code development mode control is enabled
    if (jeecgBaseConfig == null) {
      jeecgBaseConfig = SpringContextUtils.getBean(JeecgBaseConfig.class);
    }

    if (jeecgBaseConfig.getFirewall() != null
        && LowCodeModeInterceptor.LOW_CODE_MODE_PROD.equals(
            jeecgBaseConfig.getFirewall().getLowCodeMode())) {
      String requestURI = request.getRequestURI().substring(request.getContextPath().length());
      log.info("Low-code mode, intercepted request path: " + requestURI);
      LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
      Set<String> hasRoles = null;
      if (loginUser == null) {

        // Roles owned by current logged-in user
        hasRoles = commonAPI.queryUserRolesById(loginUser.getId());
      }

      log.info("get loginUser info: {}", loginUser);
      log.info("get loginRoles info: {}", hasRoles != null ? hasRoles.toArray() : "Empty");

      // Owned roles intersect with dev-allowed roles
      boolean hasIntersection = CommonUtils.hasIntersection(hasRoles, CommonConstant.allowDevRoles);
      // No restriction for super admin or dev-allowed roles
      if (loginUser != null && ("admin".equals(loginUser.getUsername()) || hasIntersection)) {
        return true;
      }

      this.returnErrorMessage(response);
      return false;
    }
    return true;
  }

  /**
   * ReturnResult
   *
   * @param response
   */
  private void returnErrorMessage(HttpServletResponse response) {
    // Return to frontend on validation failure
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json; charset=utf-8");
    PrintWriter out = null;
    try {
      out = response.getWriter();
      Result<?> result = Result.error(I18nUtil.getMessage("lowCodeMode.publishModeNotAllowed"));
      out.print(JSON.toJSON(result));
    } catch (IOException e) {
      log.error("operation failed", e);
    }
  }
}
