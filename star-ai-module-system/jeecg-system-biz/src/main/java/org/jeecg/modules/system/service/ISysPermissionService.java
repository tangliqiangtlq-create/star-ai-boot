package org.jeecg.modules.system.service;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.system.entity.SysPermission;
import org.jeecg.modules.system.model.TreeModel;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * Menu permission table service
 * </p>
 *
 * @Author scott
 * @since 2018-12-21
 */
public interface ISysPermissionService extends IService<SysPermission> {
    /**
     * Switch to Vue3 menu.
     */
	public void switchVue3Menu();
	
    /**
     * Query menus by parent id
     * @param parentId parent id
     * @return
     */
	public List<TreeModel> queryListByParentId(String parentId);
	
	/**
     * Physical delete
     * @param id menu id
     * @throws JeecgBootException
     */
	public void deletePermission(String id) throws JeecgBootException;
	/**
     * Soft delete
     * @param id menu id
     * @throws JeecgBootException
     */
	public void deletePermissionLogical(String id) throws JeecgBootException;

    /**
     * AddMenu
     * @param sysPermission SysPermissionObject
     * @throws JeecgBootException
     */
	public void addPermission(SysPermission sysPermission) throws JeecgBootException;

    /**
     * EditMenu
     * @param sysPermission SysPermissionObject
     * @throws JeecgBootException
     */
	public void editPermission(SysPermission sysPermission) throws JeecgBootException;

    /**
     * Get permissions owned by login user
     * @param username username
     * @return
     */
	public List<SysPermission> queryByUser(String username);
	
	/**
	 * Delete related SysPermissionDataRule records by permissionId
	 * 
	 * @param id
	 * @return
	 */
	public void deletePermRuleByPermId(String id);
	
	/**
	  * Query menu URLs that contain special characters
	 * @return
	 */
	public List<String> queryPermissionUrlWithStar();

	/**
	 * Check whether user has permission
	 * @param username
	 * @param sysPermission
	 * @return
	 */
	public boolean hasPermission(String username, SysPermission sysPermission);

	/**
	 * Check permission by username and request URL
	 * @param username
	 * @param url
	 * @return
	 */
	public boolean hasPermission(String username, String url);

	/**
	 * QueryDepartmentPermissionData
	 * @param departId
	 * @return
	 */
	List<SysPermission> queryDepartPermissionList(String departId);

	/**
	 * Check whether URL exists (aggregate routing allows child menu path as parent route)
	 * @param id
	 * @param url
	 * @param alwaysShow whether aggregate routing is enabled
	 * @return
	 */
	 boolean checkPermDuplication(String id, String url,Boolean alwaysShow);
}
