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

@Data
@TableName("sys_user_mfa")
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ApiModel(value = "sys_user_mfaObject", description = "User MFA factors")
public class SysUserMfa {

  @TableId(type = IdType.ASSIGN_ID)
  @ApiModelProperty(value = "Primary key")
  private String id;

  @ApiModelProperty(value = "Tenant ID")
  private Integer tenantId;

  @ApiModelProperty(value = "User ID")
  private String userId;

  @ApiModelProperty(value = "MFA type: TOTP")
  private String mfaType;

  @ApiModelProperty(value = "Encrypted secret")
  private String secretEnc;

  @ApiModelProperty(value = "Enabled: 1-yes 0-no")
  private Integer enabled;

  @ApiModelProperty(value = "Verified time")
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private java.util.Date verifiedAt;

  @ApiModelProperty(value = "Last used time")
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private java.util.Date lastUsedAt;

  @ApiModelProperty(value = "Consecutive failure count")
  private Integer failCount;

  @ApiModelProperty(value = "Locked until time")
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private java.util.Date lockedUntil;

  @ApiModelProperty(value = "Deleted: 0-normal 1-deleted")
  private Integer delFlag;

  @ApiModelProperty(value = "Created by")
  private String createBy;

  @ApiModelProperty(value = "Create time")
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private java.util.Date createTime;

  @ApiModelProperty(value = "Updated by")
  private String updateBy;

  @ApiModelProperty(value = "Update time")
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private java.util.Date updateTime;
}

