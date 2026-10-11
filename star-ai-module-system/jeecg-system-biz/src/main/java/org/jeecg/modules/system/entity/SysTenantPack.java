package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: Tenant product package
 * @Author: jeecg-boot
 * @Date:   2022-12-31
 * @Version: V1.0
 */
@Data
@TableName("sys_tenant_pack")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="sys_tenant_packObject", description="Tenant product package")
public class SysTenantPack implements Serializable {
    private static final long serialVersionUID = 1L;

	/**Primary keyid*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary keyid")
    private String id;
	/**Tenantid*/
	@Excel(name = "Tenantid", width = 15)
    @ApiModelProperty(value = "Tenantid")
    private Integer tenantId;
	/**Product package name */
	@Excel(name = "Product package name", width = 15)
    @ApiModelProperty(value = "Product package name")
    private String packName;
	/**Open status (0 not open 1 open) */
	@Excel(name = "Enabled status (0=disabled 1=enabled)", width = 15)
    @ApiModelProperty(value = "Enabled status (0=disabled 1=enabled)")
    private String status;
	/**Remark*/
	@Excel(name = "Remark", width = 15)
    @ApiModelProperty(value = "Remark")
    private String remarks;
	/**Created by*/
    @ApiModelProperty(value = "Created by")
    private String createBy;
	/**Create time*/
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "Create time")
    private Date createTime;
	/**Updated by*/
    @ApiModelProperty(value = "Updated by")
    private String updateBy;
	/**Update time*/
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "Update time")
    private Date updateTime;
    /**Product package type (default default product package custom custom product package) */
    @Excel(name = "Product package type", width = 15)
    @ApiModelProperty(value = "Product package type")
	private String packType;
    
    /**The menu id temporary field is used to transfer the new edit menu id. */
    @TableField(exist = false)
    private String permissionIds;
    
    
    /**
     * Code
     */
    private String packCode;
    
    public SysTenantPack(){
        
    }

    public SysTenantPack(Integer tenantId, String packName, String packCode){
        this.tenantId = tenantId;
        this.packCode = packCode;
        this.packName = packName;
        this.status = "1";
    }
}
