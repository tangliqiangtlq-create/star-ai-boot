package org.jeecg.modules.system.vo.tenant;

import lombok.Data;

/**
 * Used to count users under tenant product pack
 * @Author taoYan
 * @Date 2023/2/16 15:59
 **/
@Data
public class TenantPackUserCount {

    /**
     * Tenant product pack code
     */
    private String packCode;

    /**
     * User count
     */
    private String userCount;
    
}
