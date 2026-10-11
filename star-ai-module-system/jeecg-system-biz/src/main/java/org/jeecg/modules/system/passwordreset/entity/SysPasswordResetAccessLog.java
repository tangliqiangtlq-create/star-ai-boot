package org.jeecg.modules.system.passwordreset.entity;

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
@TableName("sys_password_reset_access_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "sys_password_reset_access_logObject", description = "Password reset link access log")
public class SysPasswordResetAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "Auto-increment primary key")
    private Long id;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Access time")
    private Date createTime;

    @ApiModelProperty(value = "Access token")
    private String token;

    @ApiModelProperty(value = "Associated link.id (non-empty when token is valid)")
    private String linkId;

    @ApiModelProperty(value = "META / PRE_CHECK / SUBMIT_PRE_VALIDATE / UNKNOWN")
    private String accessType;

    @ApiModelProperty(value = "OK / INVALID / EXPIRED / LOCKED / SIGN_ERR / CHALLENGE_FAIL")
    private String accessResult;

    @ApiModelProperty(value = "Fine-grained error code")
    private String accessErrorCode;

    @ApiModelProperty(value = "Client IP")
    private String clientIp;

    @ApiModelProperty(value = "Raw X-Forwarded-For header")
    private String xForwardedForRaw;

    @ApiModelProperty(value = "User-Agent")
    private String userAgent;

    @ApiModelProperty(value = "Referer")
    private String referer;

    @ApiModelProperty(value = "Accept-Language")
    private String acceptLanguage;

    @ApiModelProperty(value = "Server-side duration ms")
    private Integer serverLatencyMs;
}
