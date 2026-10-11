package org.jeecg.modules.system.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserDepart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: UserDepartmentmapperAPI
 * @author: jeecg-boot
 */
public interface SysUserDepartMapper extends BaseMapper<SysUserDepart>{

    /**
     * ApproveUseridQueryDepartmentUser
     * @param userId Userid
     * @return List<SysUserDepart>
     */
	List<SysUserDepart> getUserDepartByUid(@Param("userId") String userId);

	/**
	 *  Query users under the specified department; supports fuzzy search by real name
	 * @param orgCode
	 * @param realname
	 * @return
	 */
	List<SysUser> queryDepartUserList(@Param("orgCode") String orgCode, @Param("realname") String realname);

	/**
	 * ByDepartmentQueryDepartmentUser
	 * @param page
	 * @param orgCode
	 * @param username
	 * @param realname
	 * @return
	 */
	IPage<SysUser> queryDepartUserPageList(Page<SysUser> page, @Param("orgCode") String orgCode, @Param("username") String username, @Param("realname") String realname);

    /**
     * GetUserInfo
     * @param page
     * @param orgCode
     * @param keyword
     * @return
     */
    IPage<SysUser> getUserInformation(Page<SysUser> page,  @Param("orgCode") String orgCode,  @Param("keyword") String keyword,@Param("userId") String userId);


	/**
	 * GetUserInfo
	 * @param page
	 * @param orgCode
	 * @param keyword
	 * @return
	 */
	IPage<SysUser> getProcessUserList(Page<SysUser> page,  @Param("orgCode") String orgCode,  @Param("keyword") String keyword,  @Param("tenantId") Integer tenantId, @Param("excludeUserIdList") List<String> excludeUserIdList);

	/**
	 * Get departments under tenant by frontend department ids
	 * @param departIds
	 * @param tenantId
	 * @return
	 */
    List<String> getTenantDepart(@Param("departIds") List<String> departIds, @Param("tenantId") String tenantId);

	/**
	 * Query user-department data by current tenant and user id
	 * @param userId
	 * @param tenantId
	 * @return
	 */
	List<SysUserDepart> getTenantUserDepart(@Param("userId") String userId, @Param("tenantId") String tenantId);

	/**
	 * Delete user-department data by user id and tenant id
	 * @param userId
	 * @param tenantId
	 */
	void deleteUserDepart(@Param("userId") String userId, @Param("tenantId") String tenantId);

	/**
	 * Get user by department id and tenant id
	 * @param departId
	 * @param tenantId
	 * @return
	 */
    List<SysUser> getUsersByDepartTenantId(@Param("departId") String departId, @Param("tenantId") Integer tenantId);

	/**
	 * Count by user id and depart id to check user-department relation
	 * @param userId
	 * @param departId
	 * @return
	 */
	@Select("SELECT COUNT(*) FROM sys_user_depart WHERE user_id = #{userId} AND dep_id = #{departId}")
    Long getCountByDepartIdAndUserId(String userId, String departId);
}
