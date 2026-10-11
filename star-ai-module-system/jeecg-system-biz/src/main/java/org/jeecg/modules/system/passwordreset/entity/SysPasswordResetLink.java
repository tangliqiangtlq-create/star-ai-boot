package org.jeecg.modules.system.passwordreset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

@Data
@TableName("sys_password_reset_link")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "sys_password_reset_linkObject", description = "Password reset external link (main table)")
public class SysPasswordResetLink implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "Primary key")
    private String id;

    @ApiModelProperty(value = "Created by")
    private String createBy;

    /**
     * Legacy column {@code creator} compatibility (dual-write with create_by).
     * select=false: exclude from list queries to avoid historical environment differences.
     */
    @TableField(value = "creator", select = false)
    @ApiModelProperty(value = "Creator (legacy column creator)", hidden = true)
    private String creator;

    /**
     * Legacy column {@code challenge} compatibility (challenge is now stored in Redis only; write empty string when creating links).
     */
    @TableField(value = "challenge", select = false)
    @ApiModelProperty(value = "challenge (legacy column)", hidden = true)
    private String challenge;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Create time")
    private Date createTime;

    @ApiModelProperty(value = "Updated by")
    private String updateBy;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Update time")
    private Date updateTime;

    @ApiModelProperty(value = "Department")
    private String sysOrgCode;

    @ApiModelProperty(value = "Optimistic lock version")
    private Integer version;

    @ApiModelProperty(value = "Link token (url-safe, unique)")
    private String token;

    @ApiModelProperty(value = "HMAC-SHA256 signature")
    private String signature;

    @ApiModelProperty(value = "Token version number")
    private String tokenVersion;

    @ApiModelProperty(value = "Target account")
    private String username;

    @ApiModelProperty(value = "Target userId (redundant)")
    private String userId;

    @Excel(name = "Source", width = 20, dicCode = "password_reset_source")
    @Dict(dicCode = "password_reset_source")
    @ApiModelProperty(value = "Source")
    private String source;

    @ApiModelProperty(value = "Created by IP")
    private String creatorIp;

    @ApiModelProperty(value = "Valid duration (minutes)")
    private Integer ttlMinutes;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Absolute expiration time")
    private Date expireTime;

    @ApiModelProperty(value = "Max failure count")
    private Integer maxFailAttempts;

    @ApiModelProperty(value = "Current cumulative failure count")
    private Integer failCount;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Lock duration after exceeding failure count limit")
    private Date lockedAt;

    @Excel(name = "Status", width = 15, dicCode = "password_reset_link_status")
    @Dict(dicCode = "password_reset_link_status")
    @ApiModelProperty(value = "PENDING/USED/REVOKED/LOCKED/EXPIRED")
    private String status;

    @ApiModelProperty(value = "Whether successfully consumed: Y/N (dual-write with status to prevent concurrent overwrite)")
    private String consumedFlag;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Successful use time")
    private Date consumedTime;

    @ApiModelProperty(value = "Source IP on successful use")
    private String consumedByIp;

    @ApiModelProperty(value = "User-Agent on successful use")
    private String consumedUa;

    @ApiModelProperty(value = "Manual revoker")
    private String revokedBy;

    @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "Revocation time")
    private Date revokedTime;

    @ApiModelProperty(value = "Revocation reason")
    private String revokeReason;

    @ApiModelProperty(value = "Remark")
    private String remark;

    @ApiModelProperty(value = "Extended field JSON")
    private String extInfo;
}
