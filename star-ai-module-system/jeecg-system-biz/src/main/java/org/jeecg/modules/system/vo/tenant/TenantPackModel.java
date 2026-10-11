package org.jeecg.modules.system.vo.tenant;

import lombok.Data;

import java.util.List;

/**
 * Tenant product-pack info
 *  Includes user info + permission info
 * @Author taoYan
 * @Date 2023/2/16 21:01
 **/
@Data
public class TenantPackModel {

    /**
     * TenantId
     */
    private Integer tenantId;
    /**
     * Product pack code.
     */
    private String packCode;

    /**
     * Product pack ID.
     */
    private String packId;

    /**
     * Product pack name.
     */
    private String packName;

    /**
     * Product pack permission info.
     */
    private List<TenantPackAuth> authList;

    /**
     * Product pack user list.
     */
    private List<TenantPackUser> userList;

    /**
     * Status normal 1 application 0
     */
    private Integer packUserStatus;
    
    public Integer getPackUserStatus(){
        if(packUserStatus==null){
            return 1;
        }
        return packUserStatus; 
    }
}
