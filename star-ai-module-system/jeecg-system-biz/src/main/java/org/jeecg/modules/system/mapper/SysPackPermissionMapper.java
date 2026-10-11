package org.jeecg.modules.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.system.entity.SysPackPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @Description: Product-pack menu relation table
 * @Author: jeecg-boot
 * @Date:   2022-12-31
 * @Version: V1.0
 */
public interface SysPackPermissionMapper extends BaseMapper<SysPackPermission> {

    /**
     * Get menu ids by product-pack id
     * @param packId
     * @return
     */
    List<String> getPermissionsByPackId(@Param("packId") String packId);

    /**
     * Delete menu permissions for a product pack.
     *
     * @param tenantIdList
     */
    void deletePackPermByTenantIds(@Param("tenantIdList") List<Integer> tenantIdList);
    
}
