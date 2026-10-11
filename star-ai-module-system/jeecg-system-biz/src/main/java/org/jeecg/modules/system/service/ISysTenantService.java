package org.jeecg.modules.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.system.entity.SysTenant;
import org.jeecg.modules.system.entity.SysTenantPackUser;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.vo.tenant.TenantDepartAuthInfo;
import org.jeecg.modules.system.vo.tenant.TenantPackModel;
import org.jeecg.modules.system.vo.tenant.TenantPackUser;
import org.jeecg.modules.system.vo.tenant.TenantPackUserCount;

import java.util.Collection;
import java.util.List;

/**
 * @Description: TenantserviceAPI
 * @author: jeecg-boot
 */
public interface ISysTenantService extends IService<SysTenant> {

    /**
     * Query valid tenants
     *
     * @param idList
     * @return
     */
    List<SysTenant> queryEffectiveTenant(Collection<Integer> idList);

    /**
     * Return how many users reference a tenant
     *
     * @param id
     * @return
     */
    Long countUserLinkTenant(String id);

    /**
     * Delete tenant by ID; checks whether it is referenced
     *
     * @param id
     * @return
     */
    boolean removeTenantById(String id);

    /**
     * Invite user to tenant by phone
     * @param ids
     * @param phone
     */
    void invitationUserJoin(String ids, String phone);

    /**
     * Remove user from tenant
     * @param userIds
     * @param tenantId
     */
    void leaveTenant(String userIds, String tenantId);

    /**
     * Add tenant and add creator to relation table
     * @param sysTenant
     * @param userId
     */
    Integer saveTenantJoinUser(SysTenant sysTenant, String userId);

    /**
     * SaveTenant
     * @param sysTenant
     */
    void saveTenant(SysTenant sysTenant);

    /**
     * Join tenant by house number
     * @param sysTenant
     * @param userId
     */
    Integer joinTenantByHouseNumber(SysTenant sysTenant, String userId);

    /**
     * Count how many tenants a user created
     * 
     * @param userId
     * @return
     */
    Integer countCreateTenantNum(String userId);

    /**
     * Get tenant recycle-bin data
     * @param page
     * @param sysTenant
     * @return
     */
    IPage<SysTenant> getRecycleBinPageList(Page<SysTenant> page, SysTenant sysTenant);

    /**
     * Permanently delete tenant
     * @param ids
     */
    void deleteTenantLogic(String ids);

    /**
     * Restore tenant
     * @param ids
     */
    void revertTenantLogic(String ids);

    /**
     * Leave tenant
     * @param userId
     * @param userId
     * @param username
     */
    void exitUserTenant(String userId, String username, String tenantId);

    /**
     * Change tenant owner
     * @param userId
     * @param tenantId
     */
    void changeOwenUserTenant(String userId, String tenantId);

    /**
     * Invite user to tenant via phone match
     * @param phone
     * @param departId
     * @return
     */
    Result<String> invitationUser(String phone, String departId);

    /**
     * On app-org page, check whether user has super-admin permission
     * @param tenantId
     * @return
     */
    TenantDepartAuthInfo getTenantDepartAuthInfo(Integer tenantId);


    /**
     * Get tenant product pack member count for 3 default admin packs
     * @param tenantId
     * @return
     */
    List<TenantPackUserCount> queryTenantPackUserCount(Integer tenantId);

    /**
     * Query tenant product pack information
     * @param model
     * @return
     */
    TenantPackModel queryTenantPack(TenantPackModel model);

    /**
     * Add multiple user-product pack relation records
     * @param sysTenantPackUser
     */
    void addBatchTenantPackUser(SysTenantPackUser sysTenantPackUser);

    /**
     * Add user-product pack relation with audit log
     * @param sysTenantPackUser
     */
    void addTenantPackUser(SysTenantPackUser sysTenantPackUser);

    /**
     * Remove user-product pack relation with audit log
     * @param sysTenantPackUser
     */
    void deleteTenantPackUser(SysTenantPackUser sysTenantPackUser);


    /**
     * Query list of applicant users
     */
    List<TenantPackUser> getTenantPackApplyUsers(Integer tenantId);

    /**
     * Individual applies to become administrator.
     * @param sysTenantPackUser
     */
    void doApplyTenantPackUser(SysTenantPackUser sysTenantPackUser);

    /**
     * Application approved; become admin
     * @param sysTenantPackUser
     */
    void passApply(SysTenantPackUser sysTenantPackUser);

    /**
     * Reject application to become administrator
     * @param sysTenantPackUser
     */
    void deleteApply(SysTenantPackUser sysTenantPackUser);

    /**
     * Product pack user list.
     * @param tenantId
     * @param packId
     * @param status
     * @param page
     * @return
     */
    IPage<TenantPackUser> queryTenantPackUserList(String tenantId, String packId, Integer status, Page<TenantPackUser> page);

    /**
     * Check whether admin application has already been submitted
     * @return
     */
    Long getApplySuperAdminCount();

    /**
     * Send an approve or reject message
     * 
     * @param user 
     * @param content 
     */
    void sendMsgForAgreeAndRefuseJoin(SysUser user, String content);

    /**
     * Delete current user by password
     * 
     * @param sysUser
     * @param tenantId
     */
    void deleteUserByPassword(SysUser sysUser, Integer tenantId);

    /**
     * ByUseridGetTenantInfo
     * @param userId
     * @return
     */
    List<SysTenant> getTenantListByUserId(String userId);
}
