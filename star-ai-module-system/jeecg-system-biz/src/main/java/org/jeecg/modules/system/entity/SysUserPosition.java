package org.jeecg.modules.system.entity;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;
import java.util.Date;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * @Description: User position relationship table
 * @Author: jeecg-boot
 * @Date:   2023-02-14
 * @Version: V1.0
 */
@ApiModel(value="sys_user_positionObject", description="User position relationship table")
@Data
@TableName("sys_user_position")
public class SysUserPosition implements Serializable {
    private static final long serialVersionUID = 1L;

	/**Primary key*/
	@TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary key")
    private String id;
	/**Userid*/
	@Excel(name = "Userid", width = 15)
    @ApiModelProperty(value = "Userid")
    private String userId;
	/**Position id */
    @ApiModelProperty(value = "Position id")
    private String positionId;
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
