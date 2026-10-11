package org.jeecg.modules.system.service;


import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserDepart;
import org.jeecg.modules.system.model.DepartIdModel;


import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * SysUserDepart user-org service
 * </p>
 * @Author ZhiLin
 *
 */
public interface ISysUserDepartService extends IService<SysUserDepart> {
	

	/**
	 * Query department info by user id
	 * @param userId
	 * @return
	 */
	List<DepartIdModel> queryDepartIdsOfUser(String userId);
	

	/**
	 * ByDepartmentidQueryUserInfo
	 * @param depId
	 * @return
	 */
	List<SysUser> queryUserByDepId(String depId);
  	/**
	 * Query users in current department and sub-departments by dept code
     * @param depCode department code
     * @param realname real name
     * @return List<SysUser>
	 */
	List<SysUser> queryUserByDepCode(String depCode,String realname);

	/**
	 * User component data query
	 * @param departId
	 * @param username
	 * @param pageSize
	 * @param pageNo
     * @param realname
     * @param id
     * @param isMultiTranslate whether to translate multiple fields
	 * @return
	 */
	IPage<SysUser> queryDepartUserPageList(String departId, String username, String realname, int pageSize, int pageNo,String id,String isMultiTranslate);

    /**
     * GetUserInfo
	 * @param tenantId
     * @param departId
     * @param keyword
     * @param pageSize
     * @param pageNo
     * @return
     */
    IPage<SysUser> getUserInformation(Integer tenantId, String departId, String keyword, Integer pageSize, Integer pageNo);

	/**
	 * GetUserInfo
	 * @param tenantId
	 * @param departId
	 * @param roleId
	 * @param keyword
	 * @param pageSize
	 * @param pageNo
	 * @return
	 */
	IPage<SysUser> getUserInformation(Integer tenantId,String departId,String roleId, String keyword, Integer pageSize, Integer pageNo, String excludeUserIdList);

	/**
	 * Get multiple users by department id and tenant id
	 * @param departId
	 * @param tenantId
	 * @return
	 */
	List<SysUser> getUsersByDepartTenantId(String departId,Integer tenantId);
}
