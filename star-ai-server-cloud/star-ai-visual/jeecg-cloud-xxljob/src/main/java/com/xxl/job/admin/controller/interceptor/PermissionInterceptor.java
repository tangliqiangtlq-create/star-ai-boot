package com.xxl.job.admin.controller.interceptor;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.service.LoginService;
import com.xxl.job.core.util.XxlJobRemotingUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Permission interception
 *
 * @author xuxueli 2015-12-12 18:09:04
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

	@Resource
	private LoginService loginService;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		
		if (!(handler instanceof HandlerMethod)) {
			return true;
		}

		// if need login
		boolean needLogin = true;
		boolean needAdminuser = false;
		HandlerMethod method = (HandlerMethod)handler;
		PermissionLimit permission = method.getMethodAnnotation(PermissionLimit.class);
		if (permission!=null) {
			needLogin = permission.limit();
			needAdminuser = permission.adminuser();
		}

		if (needLogin) {
				// Check accessToken and skip login verification if valid
				if (validAccessToken(request)) {
					// accessToken is verified and access is allowed
					return true;
				}

				XxlJobUser loginUser = loginService.ifLogin(request, response);
				if (loginUser == null) {
					response.sendRedirect(request.getContextPath() + "/toLogin");
					//request.getRequestDispatcher("/toLogin").forward(request, response);
					return false;
				}
				if (needAdminuser && loginUser.getRole()!=1) {
					throw new RuntimeException(I18nUtil.getString("system_permission_limit"));
				}
				
				// Verify trusted data and store it instead of directly storing unsanitized data, adhering to the trust boundary principle.
				if (loginUser != null && loginUser.getUsername() != null && loginUser.getUsername().matches("^[a-zA-Z0-9_]+$")) {
					request.setAttribute(LoginService.LOGIN_IDENTITY_KEY, loginUser);
				} else {
					throw new RuntimeException("Invalid user identity");
				}
			}

		return true;
	}
	
	/**
	 * Validate accessToken; allow access if valid
	 * @param request HTTP request
	 * @return true if accessToken is valid, otherwise false
	 */
	private boolean validAccessToken(HttpServletRequest request) {
		String accessToken = request.getHeader(XxlJobRemotingUtil.XXL_JOB_ACCESS_TOKEN);
		if (accessToken != null && accessToken.trim().length() > 0) {
			String configAccessToken = XxlJobAdminConfig.getAdminConfig().getAccessToken();
			if (configAccessToken != null && configAccessToken.trim().length() > 0) {
				return configAccessToken.equals(accessToken);
			}
		}
		return false;
	}
	
}
