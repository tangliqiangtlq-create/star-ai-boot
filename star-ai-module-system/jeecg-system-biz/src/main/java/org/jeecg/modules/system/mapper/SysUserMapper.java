package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.jeecg.modules.system.model.SysUserSysDepartModel;
import org.jeecg.modules.system.vo.SysUserDepVo;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * UserTable Mapper API
 * </p>
 *
 * @Author scott
 * @since 2018-12-20
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
	/**
	  * Query user info by account
	 * @param username
	 * @return
	 */
	public SysUser getUserByName(@Param("username") String username);

	/**
	 * C6-safe variant of {@link #getUserByName(String)} for already-authenticated callers (e.g.
	 * /sys/user/getUserInfo). Selects only columns in
	 * {@code SensitiveTableBlacklistInterceptor.SYS_USER_ALLOWED_COLUMNS} so the query passes the
	 * firewall for non-admin logged-in users, and avoids leaking password/salt/reason/
	 * is_sender/wallet_address/financial_state/kyc_attachment_* into the JSON response body.
	 * Use {@link #getUserByName(String)} instead when credential verification needs password/salt
	 * (caller must be anonymous — C6-2 short-circuits for /sys/login).
	 */
	public SysUser getUserByNameForInfo(@Param("username") String username);

	/**
	 * P3: minimal credential-projection lookup used ONLY by the internal S2S endpoint
	 * {@code POST /sys/api/verifyPassword} (system verifies a plaintext password on behalf of
	 * dcs instead of shipping the stored hash over Feign).
	 *
	 * <p>Selects just username/password/salt. The corresponding MappedStatement id is pinned in
	 * {@code SensitiveTableBlacklistInterceptor.S2S_CREDENTIAL_VERIFY_MAPPER_IDS}; the C6 column
	 * gate allows it solely for trusted {@code s2s:<module>} sessions established by
	 * {@code InternalTrustedS2sFilter} after CIDR + shared-secret checks. Interactive anonymous
	 * or admin login flows keep using {@link #getUserByName(String)}.
	 */
	public SysUser selectCredentialByUsername(@Param("username") String username);

	/**
	 * L27-2: self credential verification. Projects ONLY id/password for the caller's own account
	 * so {@code LoginController.verifyLoginPassword} (second-auth EMAIL_PASSWORD_REQUIRED, used by
	 * setPaymentPassword / changePaymentPassword / disableTotp) can compare the login password
	 * without MyBatis-Plus projecting the whole row.
	 *
	 * <p>The MappedStatement id is pinned in
	 * {@code SensitiveTableBlacklistInterceptor.SELF_CREDENTIAL_VERIFY_MAPPER_IDS}. Unlike
	 * {@link #selectCredentialByUsername(String)} (S2S-only), the C6 gate allows this one for any
	 * logged-in session <b>only when the bound id equals the current Sa-Token login id</b>, so a
	 * foreign id still raises C6_COLUMN_BLOCKED. Do not call it with another user's id.
	 *
	 * @param id user id, must equal {@code StpUtil.getLoginIdAsString()}
	 * @return user populated with id + password only, or {@code null} when not found
	 */
	@Select("SELECT id, password FROM sys_user WHERE id = #{id} AND del_flag = 0")
	public SysUser selectCredentialById(@Param("id") String id);

	/**
	 * C6-4: KYC attachment slot projection — the ONLY statement permitted to project
	 * {@code sys_user.kyc_attachment_1/2/3}.
	 *
	 * <p>Those three columns are deliberately excluded from
	 * {@code SensitiveTableBlacklistInterceptor.SYS_USER_ALLOWED_COLUMNS} because they hold the OSS
	 * objectIds of identity documents. The generic MyBatis-Plus projection used before this change
	 * ({@code getOne(wrapper.select(SysUser::getKycAttachment*))}) therefore raised
	 * {@code C6_COLUMN_BLOCKED} for every non-owner-admin caller: any custom role granted
	 * {@code system:user:edit} on {@code /sys/user/kycAttachment/adminSave}, and a plain user
	 * reading their own slots via {@code /sys/user/kycAttachment/mine}.
	 *
	 * <p>The MappedStatement id is pinned in
	 * {@code SensitiveTableBlacklistInterceptor.SYS_USER_KYC_SLOT_MAPPER_IDS}. The gate admits it
	 * only when the caller either owns the row ({@code id == StpUtil.getLoginIdAsString()}) or holds
	 * {@code system:user:edit} (the same permission {@code SysUserKycAttachmentController} already
	 * enforces), and it additionally pins the projected columns to exactly these four. The global
	 * {@code SYS_USER_ALLOWED_COLUMNS} whitelist is NOT widened, so {@code SELECT *} and every other
	 * {@code sys_user} projection stay blocked.
	 *
	 * @param id user id
	 * @return user populated with id + the three KYC slots only, or {@code null} when not found
	 */
	@Select("SELECT id, kyc_attachment_1, kyc_attachment_2, kyc_attachment_3 FROM sys_user WHERE id = #{id} AND del_flag = 0")
	public SysUser selectKycSlotsById(@Param("id") String id);

	/**
	 * C6-safe replacement for {@link BaseMapper#selectById(java.io.Serializable)}: loads a user
	 * with only {@link SysUserSafeQuery#COLUMNS} (whitelisted) columns. Use this instead of
	 * selectById in any authenticated context (interactive request or S2S/Feign). Credential
	 * verification flows that need password/salt must keep using {@link #getUserByName(String)}
	 * in an anonymous context instead.
	 *
	 * @param id user id
	 * @return user populated with whitelisted columns only, or {@code null} when not found
	 */
	default SysUser selectInfoById(Serializable id) {
		return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
				.select(SysUserSafeQuery.COLUMNS)
				.eq("id", id));
	}
	
	/**
	  * Query user ID by account
	 * @param username
	 * @return
	 */
	public String getUserIdByName(@Param("username") String username);

	/**
	 *  Query users by department ID
	 * @param page
	 * @param departId
     * @param username user login account
	 * @return
	 */
	IPage<SysUser> getUserByDepId(Page page, @Param("departId") String departId, @Param("username") String username);

	/**
	 * All user accounts under department and sub-departments
	 *
	 * @param orgCode department code
	 * @return
	 */
	List<String> getUserAccountsByDepCode(@Param("orgCode") String orgCode);

	/**
	 *  Query department names for users by user IDs
	 * @param userIds
	 * @return
	 */
	List<SysUserDepVo> getDepNamesByUserIds(@Param("userIds")List<String> userIds);

	/**
	 *  Query users under departments by department IDs
	 * @param page
	 * @param departIds
     * @param username user login account
	 * @return
	 */
	IPage<SysUser> getUserByDepIds(Page page, @Param("departIds") List<String> departIds, @Param("username") String username);

	/**
	 * Query users by role id
	 * @param page
	 * @param roleId role id
     * @param username login account
	 * @return
	 */
	IPage<SysUser> getUserByRoleId(Page page, @Param("roleId") String roleId, @Param("username") String username);

	/**
	 * Query users by multiple role ids (foreach bind, no SQL string concat)
	 * @param page
	 * @param roleIds role id list
	 * @param username login account
	 * @return
	 */
	IPage<SysUser> getUserByRoleIds(Page page, @Param("roleIds") List<String> roleIds, @Param("username") String username);

	/**
	 * Query user ids by multiple role ids (foreach bind for Controller inSql replacement)
	 * @param roleIds role id list
	 * @return user id list
	 */
	List<String> getUserIdsByRoleIds(@Param("roleIds") List<String> roleIds);
	
	/**
	 * Set department ID by username
	 * @param username
	 * @param orgCode
	 */
	void updateUserDepart(@Param("username") String username,@Param("orgCode") String orgCode, @Param("loginTenantId") Integer loginTenantId);
	
	/**
	 * Query user info by phone number
	 * @param phone
	 * @return
	 */
	public SysUser getUserByPhone(@Param("phone") String phone);
	
	
	/**
	 * Query user info by email
	 * @param email
	 * @return
	 */
	public SysUser getUserByEmail(@Param("email")String email);

	/**
	 * Query users by orgCode, including sub-departments
	 *
	 * @param page pagination object; XML can read from it; pass Page for auto pagination (must be first; extend Page for custom pagination)
	 * @param orgCode
	 * @param userParams user query conditions; may be null
	 * @return
	 */
	List<SysUserSysDepartModel> getUserByOrgCode(IPage page, @Param("orgCode") String orgCode, @Param("userParams") SysUser userParams);


    /**
     * Query total count for getUserByOrgCode
     *
     * @param orgCode
     * @param userParams user query criteria; may be null
     * @return
     */
    Integer getUserByOrgCodeTotal(@Param("orgCode") String orgCode, @Param("userParams") SysUser userParams);

    /**
     * Batch delete role-user relations
     * @Author scott
     * @Date 2019/12/13 16:10
     * @param roleIdArray
     */
	void deleteBathRoleUserRelation(@Param("roleIdArray") String[] roleIdArray);

    /**
     * Batch delete role-permission relations
     * @Author scott
     * @Date 2019/12/13 16:10
     * @param roleIdArray
     */
	void deleteBathRolePermissionRelation(@Param("roleIdArray") String[] roleIdArray);

	/**
	 * Query logically deleted users
     * @param wrapper
     * @return List<SysUser>
	 */
	List<SysUser> selectLogicDeleted(@Param(Constants.WRAPPER) Wrapper<SysUser> wrapper);

	/**
	 * Restore logically deleted users
     * @param userIds user ids
     * @param entity
     * @return int
	 */
	int revertLogicDeleted(@Param("userIds") List<String> userIds, @Param("entity") SysUser entity);

	/**
	 * Permanently delete logically deleted users
     * @param userIds multiple user ids
     * @return int
	 */
	int deleteLogicDeleted(@Param("userIds") List<String> userIds);

    /**
     * Update empty strings to null [this pattern has SQL injection risk; do not use casually]
     * @param fieldName
     * @return int
     */
    @Deprecated
    int updateNullByEmptyString(@Param("fieldName") String fieldName);
    
	/**
	 *  Query users under departments by department IDs
	 * @param departIds
     * @param username user account name
	 * @return
	 */
	List<SysUser> queryByDepIds(@Param("departIds")List<String> departIds,@Param("username") String username);

	/**
	 * GetUserInfo
	 * @param page
	 * @param roleId
	 * @param keyword
	 * @param userIdList
	 * @return
	 */
	IPage<SysUser> selectUserListByRoleId(Page<SysUser> page,  @Param("roleId") String roleId,  @Param("keyword") String keyword,  @Param("tenantId") Integer tenantId, @Param("excludeUserIdList") List<String> excludeUserIdList);

    /**
     * Update delete status and departure status
     * @param userIds  collection of user IDs
     * @param sysUser
     * @return boolean
     */
    void updateStatusAndFlag(@Param("userIds") List<String> userIds, @Param("sysUser") SysUser sysUser);

	/**
	 * Get resigned user list under tenant
	 * @param tenantId
	 * @return
	 */
	List<SysUser> getTenantQuitList(@Param("tenantId") Integer tenantId);
	
	/**
	 * Get active user ids under tenant
	 * @param tenantId
	 * @return
	 */
	List<String> getTenantUserIdList(@Param("tenantId") Integer tenantId);
	
	/**
	 * Get user data by department ids and tenant id
	 * @param departIds
	 * @param tenantId
	 * @return
	 */
	List<SysUser> getUserByDepartsTenantId(@Param("departIds") List<String> departIds,@Param("tenantId") Integer tenantId);

	/**
	 * Get user by username and phone
	 * @param phone
	 * @param username
	 * @return
	 */
	@Select("select id,phone from sys_user where phone = #{phone} and username = #{username}")
    SysUser getUserByNameAndPhone(@Param("phone") String phone, @Param("username") String username);

	/**
	 * Count registrations by role ID and date range
	 * @param roleId role ID; null queries all
	 * @param startDate start date
	 * @param endDate end date
	 * @return registration count
	 */
	@Select("<script>" +
			"SELECT COUNT(*) FROM sys_user WHERE create_time BETWEEN #{startDate} AND #{endDate} " +
			"<if test='roleId != null and roleId != \"\"'>" +
			"AND id IN (SELECT user_id FROM sys_user_role WHERE role_id = #{roleId})" +
			"</if>" +
			"</script>")
	Long countRegistrationByRoleAndDateRange(@Param("roleId") String roleId, @Param("startDate") java.util.Date startDate, @Param("endDate") java.util.Date endDate);

	/** Password-reset external link: write new password + last change time + update_by */
	@Update("UPDATE sys_user SET password = #{newPwd}, last_password_change_time = #{lastPasswordChangeTime}, "
			+ "update_by = #{updateBy}, update_time = #{updateTime} "
			+ "WHERE username = #{username} AND del_flag = 0")
	int updatePasswordAndLastChangeTimeByUsername(@Param("newPwd") String newPwd,
												  @Param("updateBy") String updateBy,
												  @Param("updateTime") java.util.Date updateTime,
												  @Param("lastPasswordChangeTime") java.util.Date lastPasswordChangeTime,
												  @Param("username") String username);

	/** On concurrency conflict, rollback password / lastPasswordChangeTime / update_by/time */
	@Update("UPDATE sys_user SET password = #{oldPwd}, last_password_change_time = #{oldLastPasswordChangeTime}, "
			+ "update_by = #{oldUpdateBy}, update_time = #{oldUpdateTime} "
			+ "WHERE username = #{username} AND del_flag = 0")
	int rollbackPasswordByUsername(@Param("oldPwd") String oldPwd,
								   @Param("oldUpdateBy") String oldUpdateBy,
								   @Param("oldUpdateTime") java.util.Date oldUpdateTime,
								   @Param("oldLastPasswordChangeTime") java.util.Date oldLastPasswordChangeTime,
								   @Param("username") String username);
}
