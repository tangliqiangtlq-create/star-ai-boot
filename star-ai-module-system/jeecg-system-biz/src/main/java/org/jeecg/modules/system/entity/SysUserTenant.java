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
 * @Description: sys_user_tenant_relation
 * @Author: jeecg-boot
 * @Date:   2022-12-23
 * @Version: V1.0
 */
@Data
@TableName("sys_user_tenant")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="sys_user_tenantObject", description="sys_user_tenant")
public class SysUserTenant implements Serializable {
    private static final long serialVersionUID = 1L;

	/**Primary keyid*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary keyid")
    private String id;
	/**Userid*/
	@Excel(name = "Userid", width = 15)
    @ApiModelProperty(value = "Userid")
    private String userId;
	/**Tenantid*/
	@Excel(name = "Tenantid", width = 15)
    @ApiModelProperty(value = "Tenantid")
    private Integer tenantId;
	/**Status (1 normal 2 frozen 3 pending review 4 rejected) */
	@Excel(name = "Status (1=normal, 2=frozen, 3=pending review, 4=rejected)", width = 15)
    @ApiModelProperty(value = "Status (1=normal, 2=frozen, 3=pending review, 4=rejected)")
    private String status;
	/**Creator login name */
    @ApiModelProperty(value = "Creator login name")
    private String createBy;
	/**Creation date */
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "Creation date")
    private Date createTime;
	/**Updater login name */
    @ApiModelProperty(value = "Updater login name")
    private String updateBy;
	/**Update date */
	@JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
    @ApiModelProperty(value = "Update date")
    private Date updateTime;
}