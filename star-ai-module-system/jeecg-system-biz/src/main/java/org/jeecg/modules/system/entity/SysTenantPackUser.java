package org.jeecg.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * @Description: Tenant product package user relationship table
 * @Author: jeecg-boot
 * @Date:   2023-02-16
 * @Version: V1.0
 */
@Data
@TableName("sys_tenant_pack_user")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="sys_tenant_pack_userObject", description="Tenant product package user relationship table")
public class SysTenantPackUser implements Serializable {
    private static final long serialVersionUID = 1L;

    /**id*/
    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "id")
    private java.lang.String id;
    /**Tenant product package ID */
    @Excel(name = "Tenant product package ID", width = 15)
    @ApiModelProperty(value = "Tenant product package ID")
    private java.lang.String packId;
    /**UserID*/
    @Excel(name = "UserID", width = 15)
    @ApiModelProperty(value = "UserID")
    private java.lang.String userId;
    /**TenantID*/
    @Excel(name = "TenantID", width = 15)
    @ApiModelProperty(value = "TenantID")
    private java.lang.Integer tenantId;
    /**Created by*/
    @ApiModelProperty(value = "Created by")
    private java.lang.String createBy;
    /**Create time*/
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "Create time")
    private java.util.Date createTime;
    /**Updated by*/
    @ApiModelProperty(value = "Updated by")
    private java.lang.String updateBy;
    /**Update time*/
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "Update time")
    private java.util.Date updateTime;

    private transient String realname;

    private transient String packName;

    private transient String packCode;

    /**
     * Status (application 0 normal 1)
     */
    private Integer status;

    public SysTenantPackUser(){
        
    }
    public SysTenantPackUser(Integer tenantId, String packId, String userId) {
        this.packId = packId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.status = 1;
    }

    public SysTenantPackUser(SysTenantPackUser param, String userId, String realname) {
        this.userId = userId;
        this.realname = realname;
        this.packId = param.getPackId();
        this.tenantId = param.getTenantId();
        this.packName = param.getPackName();
        this.status = 1;
    }
}
