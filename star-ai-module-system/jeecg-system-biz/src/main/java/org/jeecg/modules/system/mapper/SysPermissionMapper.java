package org.jeecg.modules.system.mapper;

import java.util.List;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.system.entity.SysPermission;
import org.jeecg.modules.system.model.TreeModel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * MenuPermissionTable Mapper API
 * </p>
 *
 * @Author scott
 * @since 2018-12-21
 */
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
	/**
	   * Query child menus by parent menu ID
	 * @param parentId
	 * @return
	 */
	public List<TreeModel> queryListByParentId(@Param("parentId") String parentId);
	
	/**
	 * ByUserQueryUserPermission
     * @param userId UserID
     * @return List<SysPermission>
	 */
	public List<SysPermission> queryByUser(@Param("userId") String userId);
	
	/**
	 * Update menu status field: whether it is a child node
     * @param id menu id
     * @param leaf leaf node
     * @return int
	 */
	@Update("update sys_permission set is_leaf=#{leaf} where id = #{id}")
	public int setMenuLeaf(@Param("id") String id,@Param("leaf") int leaf);

	/**
	 * Switch to Vue3 menu
	 */
	@Update("alter table sys_permission rename to sys_permission_v2")
	public void backupVue2Menu();
	@Update("alter table sys_permission_v3 rename to sys_permission")
	public void changeVue3Menu();
	
	/**
	 * Get data-permission URLs with fuzzy match rules
     * @return List<String>
	 */
	@Select("SELECT url FROM sys_permission WHERE del_flag = 0 and menu_type = 2 and url like '%*%'")
    public List<String> queryPermissionUrlWithStar();


	/**
	 * Query menu permissions by user account
	 * @param sysPermission
	 * @param username
	 * @return
	 */
	public int queryCountByUsername(@Param("username") String username, @Param("permission") SysPermission sysPermission);


	/**
	 * QueryDepartmentPermissionData
	 * @param departId
	 * @return
	 */
	List<SysPermission> queryDepartPermissionList(@Param("departId") String departId);

	/**
	 * Query permissions by username and test role id
	 * @return
	 */
	@InterceptorIgnore(tenantLine = "true")
    List<SysPermission> queryPermissionByTestRoleId();
}
