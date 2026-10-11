package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.system.entity.SysTenant;
import org.jeecg.modules.system.vo.tenant.TenantPackUser;
import org.jeecg.modules.system.vo.tenant.TenantPackUserCount;
import org.jeecg.modules.system.vo.tenant.UserDepart;
import org.jeecg.modules.system.vo.tenant.UserPosition;

import java.util.List;

/**
 * @Description: TenantmapperAPI
 * @author: jeecg-boot
 */
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /**
     * Get max id
     */
    @Select("select MAX(id) id FROM sys_tenant")
    Integer getMaxTenantId();
    
    /**
     * Get soft-deleted tenant recycle-bin data
     * @param page
     * @param sysTenant
     * @return
     */
    List<SysTenant> getRecycleBinPageList(@Param("page") Page<SysTenant> page, @Param("sysTenant") SysTenant sysTenant);
    
    /**
     * Permanently delete tenant
     * @param tenantId
     */
    Integer deleteByTenantId(@Param("tenantIds") List<Integer> tenantId);

    /**
     * Restore tenant
     * @param list
     * @return
     */
    Integer revertTenantLogic(@Param("tenantIds")List<Integer> list);

    /**
     * Count tenant product pack members
     * @param tenantId
     * @return
     */
    List<TenantPackUserCount> queryTenantPackUserCount(@Param("tenantId") Integer tenantId);

    /**
     * Query whether the user is a super admin of the tenant product pack
     * @param tenantId
     * @param userId
     * @return
     */
    Integer querySuperAdminCount(@Param("tenantId") Integer tenantId, @Param("userId") String userId);

    /**
     * Query product pack code for a user
     * @param tenantId
     * @param userId
     * @return
     */
    List<String> queryUserPackCode(@Param("tenantId") Integer tenantId, @Param("userId") String userId);

    /**
     * Query users associated with a product pack
     * @param tenantId
     * @param packId
     * @param packUserStatus
     * @return
     */
    List<TenantPackUser> queryPackUserList(@Param("tenantId") Integer tenantId, @Param("packId") String packId, @Param("packUserStatus") Integer packUserStatus);


    /**
     * ByUserID QueryDepartment
     * @param userIdList
     * @return
     */
    List<UserDepart> queryUserDepartList(@Param("userIdList") List<String> userIdList);

    /**
     * Query positions by user ID list
     * @param userIdList
     * @return
     */
    List<UserPosition> queryUserPositionList(@Param("userIdList") List<String> userIdList);

    /**
     * Query users associated with a product pack
     * @param page
     * @param tenantId
     * @param packId
     * @param status
     * @return
     */
    List<TenantPackUser> queryTenantPackUserList(@Param("page") Page<TenantPackUser> page, @Param("tenantId") String tenantId, @Param("packId") String packId, @Param("status") Integer status);


    /**
     * ByTenantID QueryTenant
     * @param id
     * @return
     */
    @Select("select * from sys_tenant where id = #{id}")
    SysTenant querySysTenant(@Param("id") Integer id);

    /**
     * Check whether super admin application has already been submitted
     * @param userId
     * @param tenantId
     * @return
     */
    Long getApplySuperAdminCount(@Param("userId") String userId, @Param("tenantId") Integer tenantId);

    /**
     * Check whether tenant exists
     * @param tenantId
     * @return
     */
    @Select("select count(1) from sys_tenant where id = #{tenantId} and del_flag = 0")
    Long tenantIzExist(@Param("tenantId") Integer tenantId);

    /**
     * ByUseridGetTenant
     * @param userId
     * @return
     */
    List<SysTenant> getTenantListByUserId(@Param("userId") String userId);
}
