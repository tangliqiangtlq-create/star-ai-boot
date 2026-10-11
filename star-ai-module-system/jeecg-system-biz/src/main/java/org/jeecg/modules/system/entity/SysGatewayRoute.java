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
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: gateway routing management
 * @Author: jeecg-boot
 * @Date:   2020-05-26
 * @Version: V1.0
 */
@Data
@TableName("sys_gateway_route")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value="sys_gateway_routeObject", description="Gateway route management")
public class SysGatewayRoute implements Serializable {
    private static final long serialVersionUID = 1L;

    /**Primary key*/
    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary key")
    private String id;

    /**routerKEy*/
    @ApiModelProperty(value = "Route ID")
    private String routerId;

    /**Service name */
    @Excel(name = "Service name", width = 15)
    @ApiModelProperty(value = "Service name")
    private String name;

    /**Service address */
    @Excel(name = "Service address", width = 15)
    @ApiModelProperty(value = "Service address")
    private String uri;

    /**
     * Assertion configuration
     */
    private String predicates;

    /**
     * FilterConfig
     */
    private String filters;

    /**Whether to ignore the prefix 0-no 1-yes */
    @Excel(name = "Ignore prefix", width = 15)
    @ApiModelProperty(value = "Ignore prefix")
    @Dict(dicCode = "yn")
    private Integer stripPrefix;

    /**Whether to retry 0-no 1-yes */
    @Excel(name = "Whether retry", width = 15)
    @ApiModelProperty(value = "Whether retry")
    @Dict(dicCode = "yn")
    private Integer retryable;

    /**Whether to retain data: 0-no 1-yes */
    @Excel(name = "Retained data", width = 15)
    @ApiModelProperty(value = "Retained data")
    @Dict(dicCode = "yn")
    private Integer persistable;

    /**Whether to display in the interface document: 0-no 1-yes */
    @Excel(name = "Display in API documentation", width = 15)
    @ApiModelProperty(value = "Display in API documentation")
    @Dict(dicCode = "yn")
    private Integer showApi;

    /**Status 1 valid 0 invalid */
    @Excel(name = "Status", width = 15)
    @ApiModelProperty(value = "Status")
    @Dict(dicCode = "yn")
    private Integer status;

    /**Created by*/
    @ApiModelProperty(value = "Created by")
    private String createBy;
    /**Creation date */
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Creation date")
    private Date createTime;
    /*    *//**Updated by*//*
    @ApiModelProperty(value = "Updated by")
    private String updateBy;
    *//**Update date *//*
    @JsonFormat(timezone = "UTC",pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Update date")
    private Date updateTime;
    *//**Department *//*
    @ApiModelProperty(value = "Owning department")
    private String sysOrgCode;*/
}
