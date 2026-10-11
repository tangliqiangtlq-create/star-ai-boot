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
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("sys_deny_audit")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "sys_deny_audit entity", description = "Denied invocation audit record")
public class SysDenyAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary key ID")
    private String id;

    @ApiModelProperty(value = "Request IP")
    private String requestIp;

    @ApiModelProperty(value = "User ID (optional; empty when not logged in)")
    private String userIdOpt;

    @ApiModelProperty(value = "HTTP method (GET/POST/PUT/DELETE/...)")
    private String httpMethod;

    @ApiModelProperty(value = "Request URI")
    private String requestUri;

    @ApiModelProperty(value = "Deny reason code (H8_DENIED_NOTLOGIN / H8_DENIED_NOPERMISSION / H8_DENIED_ACCESS / ...)")
    private String reasonCode;

    @ApiModelProperty(value = "Reason detail (redacted)")
    private String reasonDetailRedacted;

    @ApiModelProperty(value = "Request body (redacted, optional)")
    private String requestBodyRedacted;

    @ApiModelProperty(value = "Trace ID (nullable)")
    private String traceId;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Created at")
    private Date createTime;

    @ApiModelProperty(value = "Tenant ID (optional)")
    private Integer tenantId;
}
