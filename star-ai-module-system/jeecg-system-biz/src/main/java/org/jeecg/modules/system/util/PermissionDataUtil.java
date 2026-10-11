package org.jeecg.modules.system.util;

import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.system.constant.DefIndexConst;
import org.jeecg.modules.system.entity.SysPermission;

import java.util.List;

/**
 * @Author: scott
 * @Date: 2019-04-03
 */
public class PermissionDataUtil {

    /**
     * Path：views/
     */
    private static final String PATH_VIEWS = "views/";

    /**
     * Path：src/views/
     */
    private static final String PATH_SRC_VIEWS = "src/views/";

    /**
     * .vue suffix
     */
    private static final String VUE_SUFFIX = ".vue";

	/**
	 * Smart handling of bad data to simplify user mistakes
	 * 
	 * @param permission
	 */
	public static SysPermission intelligentProcessData(SysPermission permission) {
		if (permission == null) {
			return null;
		}

		// Component
		if (oConvertUtils.isNotEmpty(permission.getComponent())) {
			String component = permission.getComponent();
			if (component.startsWith(SymbolConstant.SINGLE_SLASH)) {
				component = component.substring(1);
			}
			if (component.startsWith(PATH_VIEWS)) {
				component = component.replaceFirst(PATH_VIEWS, "");
			}
			if (component.startsWith(PATH_SRC_VIEWS)) {
				component = component.replaceFirst(PATH_SRC_VIEWS, "");
			}
			if (component.endsWith(VUE_SUFFIX)) {
				component = component.replace(VUE_SUFFIX, "");
			}
			permission.setComponent(component);
		}
		
		// Request URL
		if (oConvertUtils.isNotEmpty(permission.getUrl())) {
			String url = permission.getUrl();
			if (url.endsWith(VUE_SUFFIX)) {
				url = url.replace(VUE_SUFFIX, "");
			}
			if (!url.startsWith(CommonConstant.STR_HTTP) && !url.startsWith(SymbolConstant.SINGLE_SLASH)&&!url.trim().startsWith(SymbolConstant.DOUBLE_LEFT_CURLY_BRACKET)) {
				url = SymbolConstant.SINGLE_SLASH + url;
			}
			permission.setUrl(url);
		}
		
		// Default component for top-level menus
		if (0 == permission.getMenuType() && oConvertUtils.isEmpty(permission.getComponent())) {
			// Default component for top-level menus
			permission.setComponent("layouts/RouteView");
		}
		return permission;
	}
	
	/**
	 * If no index page exists, create one and add it to the list
	 * @param metaList
	 */
	public static void addIndexPage(List<SysPermission> metaList) {
		boolean hasIndexMenu = false;
		
		for (SysPermission sysPermission : metaList) {
			if(DefIndexConst.DEF_INDEX_URL.equals(sysPermission.getUrl())) {
				hasIndexMenu = true;
				break;
			}
		}
		if(!hasIndexMenu) {
			metaList.add(0,new SysPermission(true));
		}
	}

	/**
	 * Check whether home page is authorized
	 * @param metaList
	 * @return
	 */
	public static boolean hasIndexPage(List<SysPermission> metaList){
		boolean hasIndexMenu = false;
		for (SysPermission sysPermission : metaList) {
			if(DefIndexConst.DEF_INDEX_URL.equals(sysPermission.getUrl())) {
				hasIndexMenu = true;
				break;
			}
		}
		return hasIndexMenu;
	}

	/**
	 * Check page authorization by id
	 *
	 * @param metaList
	 * @return
	 */
	public static boolean hasMenuById(List<SysPermission> metaList, String id) {
		for (SysPermission sysPermission : metaList) {
			if (id.equals(sysPermission.getId())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get default home page config
	 */
	public static Object getDefIndexConfig() {
		return null;
	}

}
