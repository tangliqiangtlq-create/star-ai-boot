package org.jeecg.modules.system.mapper;

import java.util.List;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.system.entity.SysTenant;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserTenant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.jeecg.modules.system.vo.SysUserTenantVo;
import org.jeecg.modules.system.vo.thirdapp.JwUserDepartVo;

/**
 * @Description: sys_user_tenant_relation
 * @Author: jeecg-boot
 * @Date:   2022-12-23
 * @Version: V1.0
 */
public interface SysUserTenantMapper extends BaseMapper<SysUserTenant> {

    /**
     * ApproveTenantidGetData
     * @param page
     * @param userTenantId
     * @return
     */
    List<SysUser> getPageUserList(@Param("page") Page<SysUser> page,@Param("userTenantId") Integer userTenantId,@Param("user") SysUser user);

    /**
     * ByTenantidGetUserids
     * @param tenantId
     * @return
     */
    List<String> getUserIdsByTenantId(@Param("tenantId") Integer tenantId);

    /**
     * ApproveUseridGetTenantids
     *
     * <p>Cross-tenant enumeration of a user's own memberships, called pre-login (anonymous
     * {@code /sys/login} via SysUserServiceImpl.getUserByName) where no TenantContext exists.
     * The query is keyed by an explicit user_id and intentionally returns rows across tenants,
     * so the C4 tenant-line predicate must not be injected — otherwise the tenant interceptor
     * fail-closes with C4_TENANT_CONTEXT_NULL and login itself breaks.
     * @param userId
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<Integer> getTenantIdsByUserId(@Param("userId") String userId);


    
    //==============================================================================================================================
    /**
     * ApproveUseridGetTenantList
     * @param userId
     * @return
     */
    List<SysUserTenantVo> getTenantListByUserId(@Param("userId") String userId, @Param("userTenantStatus") List<String> userTenantStatus);
    
    /**
     * Query user ids by status, login username, tenant id
     * @param tenantId
     * @param statusList
     * @param username
     * @return
     */
    List<String> getUserIdsByCreateBy(@Param("tenantId") Integer tenantId, @Param("userTenantStatus")  List<String> statusList, @Param("username") String username);

    /**
     * Join query user and tenant audit status
     * @param page
     * @param status
     * @param tenantId
     * @return
     */
    List<SysUserTenantVo> getUserTenantPageList(@Param("page") Page<SysUserTenantVo> page, @Param("status") List<String> status, @Param("user") SysUser user, @Param("tenantId") Integer tenantId);

    /**
     * Get tenant id by user id without status value (e.g. tenant exists but was rejected or pending approval)
     * @param userId
     * @return
     */
    List<Integer> getTenantIdsNoStatus(@Param("userId") String userId);
    //==============================================================================================================================

    /**
     * Count how many tenants a user created
     *
     * @param userId
     * @return
     */
    Integer countCreateTenantNum(String userId);

    /**
     * Cancel resignation
     * @param userIds
     * @param tenantId
     */
    void putCancelQuit(@Param("userIds") List<String> userIds, @Param("tenantId") Integer tenantId);

    /**
     * Check whether the current user is already under the tenant.
     * @param userId
     * @param tenantId
     */
    Integer userTenantIzExist(@Param("userId") String userId, @Param("tenantId") int tenantId);

    /**
     * Query tenants that have not been cancelled
     * @param userId
     * @return
     */
    List<SysTenant> getTenantNoCancel(@Param("userId") String userId);

    /**
     * Get my tenants by user id
     * @param page
     * @param userId
     * @param userTenantStatus
     * @return
     */
    List<SysTenant> getTenantPageListByUserId(@Param("page") Page<SysTenant> page, @Param("userId") String userId, @Param("userTenantStatus") List<String> userTenantStatus,@Param("sysUserTenantVo") SysUserTenantVo sysUserTenantVo);

    /**
     * Approve tenant join request
     * @param userId
     * @param tenantId
     */
    @Update("update sys_user_tenant set status = '1' where user_id = #{userId} and tenant_id = #{tenantId}")
    void agreeJoinTenant(@Param("userId") String userId, @Param("tenantId") Integer tenantId);

    /**
     * Reject tenant join request
     * @param userId
     * @param tenantId
     */
    @Delete("delete from sys_user_tenant where user_id = #{userId} and tenant_id = #{tenantId}")
    void refuseJoinTenant(@Param("userId") String userId, @Param("tenantId") Integer tenantId);

    /**
     * Get user-tenant relation by user id and tenant id
     *
     * @param userId
     * @param tenantId
     * @return
     */
    @Select("select id,user_id,tenant_id,create_by,status from sys_user_tenant where user_id = #{userId} and tenant_id = #{tenantId}")
    SysUserTenant getUserTenantByTenantId(@Param("userId") String userId, @Param("tenantId") Integer tenantId);

    /**
     * Delete users under tenant.
     *
     * @param tenantIds
     */
    void deleteUserByTenantId(@Param("tenantIds") List<Integer> tenantIds);

    /**
     * Get member count under tenant
     *
     * @param tenantId
     * @param tenantStatus
     * @return
     */
    Long getUserCount(Integer tenantId, String tenantStatus);
    
    /**
     * Get user data by tenant id and name
     * @param tenantId
     * @return
     */
    List<JwUserDepartVo> getUsersByTenantIdAndName(@Param("tenantId") Integer tenantId);

    /**
     * Get tenant IDs by multiple user IDs
     *
     * @param userIds
     * @return
     */
    List<Integer> getTenantIdsByUserIds(@Param("userIds") List<String> userIds);
}
