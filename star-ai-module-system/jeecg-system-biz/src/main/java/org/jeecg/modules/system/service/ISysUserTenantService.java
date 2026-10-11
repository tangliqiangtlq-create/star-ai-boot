package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.modules.system.entity.SysTenant;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.entity.SysUserTenant;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.system.vo.SysUserTenantVo;

import java.util.List;

/**
 * @Description: sys_user_tenant_relation
 * @Author: jeecg-boot
 * @Date:   2022-12-23
 * @Version: V1.0
 */
public interface ISysUserTenantService extends IService<SysUserTenant> {

    /**
     * ApproveTenantidGetData
     * @param page
     * @param userTenantId
     * @param user
     * @return
     */
    Page<SysUser> getPageUserList(Page<SysUser> page, Integer userTenantId, SysUser user);

    /**
     * Set tenant id
     * @param records
     * @return
     */
    List<SysUser> setUserTenantIds(List<SysUser> records);

    /**
     * GetTenantidGetUserids
     * @param tenantId
     * @return
     */
    List<String> getUserIdsByTenantId(Integer tenantId);

    /**
     * ApproveUseridGetTenantids
     * @param userId
     * @return
     */
    List<Integer> getTenantIdsByUserId(String userId);
    
    /**
     * ApproveUseridGetTenantList
     * @param userId
     * @param userTenantStatus
     * @return
     */
    List<SysUserTenantVo> getTenantListByUserId(String userId, List<String>  userTenantStatus);
    
    /**
     * UpdateUserTenantStatus
     * @param id
     * @param tenantId
     * @param userTenantStatus
     */
    void updateUserTenantStatus(String id, String tenantId, String userTenantStatus);

    /**
     * Join query user and tenant audit status
     * @param page
     * @param status tenant user status, default 1 normal
     * @param user
     * @return
     */
    IPage<SysUserTenantVo> getUserTenantPageList(Page<SysUserTenantVo> page, List<String> status, SysUser user, Integer tenantId);

    /**
     * Cancel resignation
     * @param userIds
     * @param tenantId
     */
    void putCancelQuit(List<String> userIds, Integer tenantId);

    /**
     * Verify whether user already exists
     * @param userId
     * @param tenantId
     * @return
     */
    Integer userTenantIzExist(String userId, Integer tenantId);

    /**
     * Get my tenants by user id
     *
     * @param page
     * @param userId
     * @param userTenantStatus
     * @param sysUserTenantVo
     * @return
     */
    IPage<SysTenant> getTenantPageListByUserId(Page<SysTenant> page, String userId, List<String> userTenantStatus,SysUserTenantVo sysUserTenantVo);

    /**
     * Approve tenant join request
     * @param userId
     * @param tenantId
     */
    void agreeJoinTenant(String userId, Integer tenantId);

    /**
     * Reject tenant join request
     * @param userId
     * @param tenantId
     */
    void refuseJoinTenant(String userId, Integer tenantId);

    /**
     * Get user-tenant relation by user id and tenant id
     * @param userId
     * @param tenantId
     * @return
     */
    SysUserTenant getUserTenantByTenantId(String userId, Integer tenantId);

    /**
     * Get member count under tenant
     * @param tenantId
     * @param tenantStatus
     * @return
     */
    Long getUserCount(Integer tenantId, String tenantStatus);
}
