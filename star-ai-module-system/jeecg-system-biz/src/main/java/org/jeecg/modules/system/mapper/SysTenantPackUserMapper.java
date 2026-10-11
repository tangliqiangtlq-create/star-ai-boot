package org.jeecg.modules.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.entity.SysTenantPackUser;

import java.util.List;

/**
 * @Description: Tenant product-pack user relation
 * @Author: jeecg-boot
 * @Date:   2023-02-16
 * @Version: V1.0
 */
public interface SysTenantPackUserMapper extends BaseMapper<SysTenantPackUser> {


    /**
     * Query users with specific roles under a tenant
     * @param tenantId
     * @param packCodeList
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    List<String> queryTenantPackUserNameList(@Param("tenantId") Integer tenantId, @Param("packCodeList") List<String> packCodeList); 

    /**
     * Check whether the current user has administrator permissions under the tenant.
     * @param userId
     * @param tenantId
     * @return
     */
    Long izHaveBuyAuth(@Param("userId") String userId, @Param("tenantId") Integer tenantId);

    /**
     * Delete users under tenant product pack by tenant id
     * @param tenantId
     */
    void deletePackUserByTenantId(@Param("tenantId") Integer tenantId, @Param("userIds") List<String> userIds);

    /**
     * Delete users under tenant product pack by multiple tenant IDs
     * @param
     */
    void deletePackUserByTenantIds(@Param("tenantIds") List<Integer> tenantIds);
}
