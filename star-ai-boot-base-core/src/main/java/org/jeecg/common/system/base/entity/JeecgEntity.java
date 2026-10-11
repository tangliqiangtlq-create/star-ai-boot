package org.jeecg.common.system.base.entity;

import java.io.Serializable;

import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: Entity base class
 * @Author: dangzhenghui@163.com
 * @Date: 2019-4-28
 * @Version: 1.1
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "ID")
    private java.lang.String id;

    /**
     * Created by
     */
    @ApiModelProperty(value = "Created by")
    @Excel(name = "Created by", width = 15)
    private java.lang.String createBy;

    /**
     * Create time
     */
    @ApiModelProperty(value = "Create time")
    @Excel(name = "Create time", width = 20, format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date createTime;

    /**
     * Updated by
     */
    @ApiModelProperty(value = "Updated by")
    @Excel(name = "Updated by", width = 15)
    private java.lang.String updateBy;

    /**
     * Update time
     */
    @ApiModelProperty(value = "Update time")
    @Excel(name = "Update time", width = 20, format = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date updateTime;

}
