package org.jeecg.common.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.common.aspect.annotation.FilePreview;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * onlineUser info @Author scott
 *
 * @since 2018-12-20
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class LoginUser {

  /** Logged-in user id */
  private String id;

  /** Login account */
  private String username;

  /** Logged-in user name */
  private String realname;

  /**
   * Login personPassword.
   *
   * <p>SEC: WRITE_ONLY so the BCrypt hash can never be serialized into an HTTP response
   * (/sys/login userInfo) nor into the distributed session cache (Redis JSON). No code reads
   * this field back off a LoginUser instance - authentication always reloads the credential
   * from the database via SysUser, so suppressing it is lossless.
   */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String password;

  /** Currently logged inDepartmentcode */
  private String orgCode;

  /** Currently logged inDepartment id */
  private String orgId;

  /** Current login rolecode(Multiple commas separated) */
  private String roleCode;

  /** Avatar */
  private String avatar;

  /** Birthday */
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd")
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private Date birthday;

  /** Gender (1: male 2: female) */
  private Integer sex;

  /** Email */
  private String email;

  /** Phone */
  private String phone;

  /** Status (1: normal, 2: frozen) */
  private Integer status;

  private Integer delFlag;

  /** Sync workflow engine: 1 sync, 0 do not sync */
  private Integer activitiSync;

  /** Create time */
  private Date createTime;

  /** identity(1 Ordinary employees 2 superior) */
  private Integer userIdentity;

  /** manageDepartment ids */
  private String departIds;

  /** Position; linked to position table */
  private String post;

  /** Landline number */
  private String telephone;

  /** Multi-tenant ids temporary field; not persisted (no DB column) */
  private String relTenantIds;

  /** Device id for uniapp push */
  private String clientId;

  private String inviterId;

  private String inviterId_dictText;

  private String customerType;

  private String customerType_dictText;

  private String reason;

  /** md5 password salt. SEC: WRITE_ONLY - see {@link #password} for rationale. */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String salt;

  private Date lastPasswordChangeTime;

  private String financialState;

  private String agent1Code;
  private String agent2Code;

  private String idType;

  private String workNo;

  private String kycStatus_dictText;

  private String kycStatus;

  /** Personal center KYC appendix1/2/3(objectId; Serialized times FilePreview Go to preview URL) */
  @FilePreview
  private String kycAttachment1;

  @FilePreview
  private String kycAttachment2;

  @FilePreview
  private String kycAttachment3;

  private Date lastLoginTime;

  private String dlygx;

  private Boolean totpEnabled;

  private String isSender;

  private String country;

  /** Whether login is allowed (0: no, 1: yes) */
  @Excel(name = "Whether login is allowed", width = 15)
  @Dict(dicCode = "status")
  private Integer allowLogin;

  /** Is a team member */
  private Boolean teamMember;

  /** The current business belongs to the userID */
  private String bizOwnerUserId;

  /** Client operation permission label */
  private List<String> permissionTags;

  /**
   * Nullify sensitive fields (password, salt, reason, lastPasswordChangeTime,
   * financialState, kycAttachment1-3, isSender) so this VO can be safely serialized
   * to a JSON response. Called by Controllers before returning to frontend.
   * Idempotent. Returns this for chaining.
   *
   * <p>This is the P0 Controller-layer defense — see CodePlan-SensitiveColumnLeakage-Fix.md
   * §3.1. LoginUser has no walletAddress field (only SysUser does), so it's omitted here.
   */
  public LoginUser clearSensitiveForResponse() {
    this.password = null;
    this.salt = null;
    this.reason = null;
    this.lastPasswordChangeTime = null;
    this.financialState = null;
    this.kycAttachment1 = null;
    this.kycAttachment2 = null;
    this.kycAttachment3 = null;
    this.isSender = null;
    return this;
  }
}
