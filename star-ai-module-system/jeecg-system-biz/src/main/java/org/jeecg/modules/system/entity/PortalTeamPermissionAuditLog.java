package org.jeecg.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("portal_team_permission_audit_log")
public class PortalTeamPermissionAuditLog implements Serializable {

  private static final long serialVersionUID = 1L;

  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  private String ownerScope;

  private String teamRoleCode;

  private String changeType;

  private String beforeTagCodes;

  private String afterTagCodes;

  private String operatorUserId;

  private String remark;

  private String createBy;

  private Date createTime;
}
