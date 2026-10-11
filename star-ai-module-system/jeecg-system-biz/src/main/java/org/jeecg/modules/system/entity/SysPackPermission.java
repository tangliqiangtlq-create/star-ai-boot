package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: Product package menu relationship table
 * @Author: jeecg-boot
 * @Date:   2022-12-31
 * @Version: V1.0
 */
@Data
@TableName("sys_tenant_pack_perms")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="sys_tenant_pack_permsObject", description="Product package menu relation table")
public class SysPackPermission implements Serializable {
    private static final long serialVersionUID = 1L;

	/**primary key number */
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary key ID")
    private String id;
	/**Tenant product package name */
	@Excel(name = "Tenant product package name", width = 15)
    @ApiModelProperty(value = "Tenant product package name")
    private String packId;
	/**Menuid*/
	@Excel(name = "Menuid", width = 15)
    @ApiModelProperty(value = "Menuid")
    private String permissionId;
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
}
