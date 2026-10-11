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
@TableName("sys_password_reset_usage_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "sys_password_reset_usage_logObject", description = "Password reset link usage log")
public class SysPasswordResetUsageLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "Auto-increment primary key")
    private Long id;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Submit time")
    private Date createTime;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Update time")
    private Date updateTime;

    @ApiModelProperty(value = "Link token")
    private String token;

    @ApiModelProperty(value = "Associated link.id")
    private String linkId;

    @ApiModelProperty(value = "Target account (redundant)")
    private String username;

    @ApiModelProperty(value = "SUCCESS / FAIL")
    private String usageResult;

    @ApiModelProperty(value = "Failure stage: SIGN_ERR/INVALID_LINK/CHALLENGE_FAIL/PWD_MISMATCH/PWD_WEAK/PWD_SAME_AS_OLD/RSA_DECRYPT/BCYPT_FAIL/CONCURRENT_CONFLICT/NOT_PERMITTED")
    private String failStage;

    @ApiModelProperty(value = "Failure summary (excluding password)")
    private String failReason;

    @ApiModelProperty(value = "Whether sys_user.password was actually written back: Y/N")
    private String passwordChanged;

    @ApiModelProperty(value = "RSA cacheKey used this time (already deleted)")
    private String rsaCacheKey;

    @ApiModelProperty(value = "Client IP")
    private String clientIp;

    @ApiModelProperty(value = "Raw X-Forwarded-For header")
    private String xForwardedForRaw;

    @ApiModelProperty(value = "User-Agent")
    private String userAgent;

    @ApiModelProperty(value = "Server-side duration for this submission ms")
    private Integer serverLatencyMs;
}
