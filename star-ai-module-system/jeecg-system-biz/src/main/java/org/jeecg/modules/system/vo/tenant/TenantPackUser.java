package org.jeecg.modules.system.vo.tenant;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Users linked to user product pack
 * @Author taoYan
 * @Date 2023/2/16 21:02
 **/
@Data
public class TenantPackUser {
    /**
     * UserID
     */
    private String id;
    
    private String username;
    
    private String realname;
    
    private String avatar;
    
    private String phone;

    /**
     * Multiple department name collection
     */
    private Set<String> departNames;

    /**
     * Multiple position name collection
     */
    private Set<String> positionNames;

    /**
     * Tenant product pack name
     */
    private String packName;

    /**
     * Tenant product pack ID
     */
    private String packId;
    
    public void addDepart(String name){
        if(departNames==null){
            departNames = new HashSet<>();
        }
        departNames.add(name);
    }


    public void addPosition(String name){
        if(positionNames==null){
            positionNames = new HashSet<>();
        }
        positionNames.add(name);
    }
}
