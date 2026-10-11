package org.jeecg.modules.system.service;

import java.util.List;

import org.jeecg.modules.system.entity.SysPermissionDataRule;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * Menu permission rule service
 * </p>
 *
 * @Author huangzhilin
 * @since 2019-04-01
 */
public interface ISysPermissionDataRuleService extends IService<SysPermissionDataRule> {

	/**
	 * Query permission data rules by menu id
	 * 
	 * @param permissionId
     * @return List<SysPermissionDataRule>
	 */
	List<SysPermissionDataRule> getPermRuleListByPermId(String permissionId);

	/**
	 * Query menu permission data from page parameters
	 * @param permRule
	 * @return
	 */
	List<SysPermissionDataRule> queryPermissionRule(SysPermissionDataRule permRule);
	
	
	/**
	  * Find data permission config by menu ID and username
	 * @param permissionId
	 * @param username
	 * @return
	 */
	List<SysPermissionDataRule> queryPermissionDataRules(String username,String permissionId);
	
	/**
	 * AddMenuPermissionConfig UpdateMenurule_flag
	 * @param sysPermissionDataRule
	 */
	public void savePermissionDataRule(SysPermissionDataRule sysPermissionDataRule);
	
	/**
	 * Delete menu permission config; check whether the menu still has permissions
	 * @param dataRuleId
	 */
	public void deletePermissionDataRule(String dataRuleId);
	
	
}
