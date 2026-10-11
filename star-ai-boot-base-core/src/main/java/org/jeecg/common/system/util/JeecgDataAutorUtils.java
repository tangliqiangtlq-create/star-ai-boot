package org.jeecg.common.system.util;

import org.jeecg.common.system.vo.SysPermissionDataRuleModel;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.common.util.SpringContextUtils;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName: JeecgDataAutorUtils
 * @Description: DataPermission queryRule container tool class
 * @Author: Zhang Daihao
 * @Date: 2012-12-15 afternoon11:27:39
 * 
 */
public class JeecgDataAutorUtils {
	
	public static final String MENU_DATA_AUTHOR_RULES = "MENU_DATA_AUTHOR_RULES";
	
	public static final String MENU_DATA_AUTHOR_RULE_SQL = "MENU_DATA_AUTHOR_RULE_SQL";
	
	public static final String SYS_USER_INFO = "SYS_USER_INFO";

	/**
	 * Inject data query conditions into the request
	 * 
	 * @param request
	 * @param dataRules
	 */
	public static synchronized void installDataSearchConditon(HttpServletRequest request, List<SysPermissionDataRuleModel> dataRules) {
		@SuppressWarnings("unchecked")
        // 1.Start withrequestgetMENU_DATA_AUTHOR_RULES，If saved, getLIST
		List<SysPermissionDataRuleModel> list = (List<SysPermissionDataRuleModel>)loadDataSearchConditon();
		if (list==null) {
			// 2.if it does not exist, thennewonelist
			list = new ArrayList<SysPermissionDataRuleModel>();
		}
		for (SysPermissionDataRuleModel tsDataRule : dataRules) {
			list.add(tsDataRule);
		}
        // 3.PastlistIncremental storage index inside
		request.setAttribute(MENU_DATA_AUTHOR_RULES, list);
	}

	/**
	 * Get data-permission rules for the request
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static synchronized List<SysPermissionDataRuleModel> loadDataSearchConditon() {
		return (List<SysPermissionDataRuleModel>) SpringContextUtils.getHttpServletRequest().getAttribute(MENU_DATA_AUTHOR_RULES);
				
	}

	/**
	 * Get data-permission SQL for the request
	 * 
	 * @return
	 */
	public static synchronized String loadDataSearchConditonSqlString() {
		return (String) SpringContextUtils.getHttpServletRequest().getAttribute(MENU_DATA_AUTHOR_RULE_SQL);
	}

	/**
	 * Inject data query conditions into the request
	 * 
	 * @param request
	 * @param sql
	 */
	public static synchronized void installDataSearchConditon(HttpServletRequest request, String sql) {
		String ruleSql = (String) loadDataSearchConditonSqlString();
		if (!StringUtils.hasText(ruleSql)) {
			request.setAttribute(MENU_DATA_AUTHOR_RULE_SQL,sql);
		}
	}

	/**
	 * Store user info in request
	 * @param request
	 * @param userinfo
	 */
	public static synchronized void installUserInfo(HttpServletRequest request, SysUserCacheInfo userinfo) {
		request.setAttribute(SYS_USER_INFO, userinfo);
	}

	/**
	 * Store user info in request
	 * @param userinfo
	 */
	public static synchronized void installUserInfo(SysUserCacheInfo userinfo) {
		SpringContextUtils.getHttpServletRequest().setAttribute(SYS_USER_INFO, userinfo);
	}

	/**
	 * Get user info from request
	 * @return
	 */
	public static synchronized SysUserCacheInfo loadUserInfo() {
		return (SysUserCacheInfo) SpringContextUtils.getHttpServletRequest().getAttribute(SYS_USER_INFO);
				
	}
}
