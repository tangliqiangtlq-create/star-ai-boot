package org.jeecg.modules.system.vo;

import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;

/**
 * @Description: User-tenant VO (user + tenant data)
 * @author: wangshuai
 * @date: 2023-01-08 17:27
 */
@Data
public class SysUserTenantVo {

    /**
     * Userid
     */
    private String id;

    /**
     * User account
     */
    private String username;

    /**
     * User nickname
     */
    private String realname;

    /**
     * Employee number
     */
    private String workNo;
    
    /**
     * Email
     */
    private String email; 
    
    /**
     * Phone number
     */
    private String phone;   
    
    /**
     * Avatar
     */
    private String avatar; 
    
    /**
     * Position
     */
    @Dict(dictTable ="sys_position",dicText = "name",dicCode = "id")
    private String post;

    /**
     * Approval status
     */
    private String status;

    /**
     * DepartmentName
     */
    private String orgCodeTxt;

    /**
     * Departmentcode
     */
    private String orgCode;

    /**
     * Tenantid
     */
    private String relTenantIds;

    /**
     * TenantCreated by
     */
    private String createBy;

    /**
     * UserTenantStatus
     */
    private String userTenantStatus;

    /**
     * UserTenantid
     */
    private String tenantUserId;

    /**
     * TenantName
     */
    private String name;

    /**
     * Industry
     */
    @Dict(dicCode = "trade")
    private String trade;
    
    /**
     * House number
     */
    private String houseNumber;

    /**
     * Whether the user is a member
     */
    private String memberType;

    /**
     * Whether the user is a tenant administrator
     */
    private Boolean tenantAdmin = false;
}
