package org.jeecg.modules.system.vo.tenant;

import lombok.Data;

/**
 * Tenant product-pack permission details
 * @Author taoYan
 * @Date 2023/2/16 21:02
 **/
@Data
public class TenantPackAuth {

    /**
     * Top-level menu.
     */
    private String category;

    /**
     * PermissionMenuName
     */
    private String authName;


    /**
     * PermissionMenuDescription
     */
    private String authNote;
}
