package org.jeecg.modules.system.vo;

import lombok.Data;

/**
 * @Description:
 * @author: wangshuai
 * @date: 2022-12-07 16:41
 */
@Data
public class SysUserRoleCountVo {
    /**
     * Roleid
     */
    private String id;
    /**
     * RoleName
     */
    private String roleName;
    /**
     * RoleDescription
     */
    private String description;
    /**
     * RoleCode
     */
    private String roleCode;
    /**
     * User count under role
     */
    private Long count;
}
