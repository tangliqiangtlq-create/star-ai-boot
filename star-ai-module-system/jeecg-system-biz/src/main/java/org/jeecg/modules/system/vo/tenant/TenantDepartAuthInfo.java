package org.jeecg.modules.system.vo.tenant;

import lombok.Data;
import org.jeecg.modules.system.entity.SysTenant;

import java.util.List;

/**
 * Enter tenant-org page: query tenant info and operation permissions
 * @Author taoYan
 * @Date 2023/2/16 16:18
 **/
@Data
public class TenantDepartAuthInfo {

    /**
     * Whether the current user is a super administrator
     */
    private boolean superAdmin;

    /**
     * TenantInfo
     */
    private SysTenant sysTenant;

    /**
     * Count tenant product pack members
     */
    private List<TenantPackUserCount> packCountList;

    /**
     * Tenant product pack code (only 3 admin product packs have this code for distinction)
     */
    private List<String> packCodes;
    
}
