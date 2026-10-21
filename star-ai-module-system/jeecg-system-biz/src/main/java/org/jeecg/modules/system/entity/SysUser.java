package org.jeecg.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
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
 * UserTable @Author scott
 *
 * @since 2018-12-20
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class SysUser implements Serializable {

  private static final long serialVersionUID = 1L;

  /** id */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** Login account */
  @Excel(name = "Login account", width = 15)
  private String username;

  /** real name */
  @Excel(name = "Real name", width = 15)
  private String realname;

  /** password */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @TableField(select = false)
  private String password;

  /** md5 password salt */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @TableField(select = false)
  private String salt;

  /** avatar */
  @Excel(name = "Avatar", width = 15, type = 2)
  @FilePreview
  private String avatar;

  /** Birthday */
  @Excel(name = "Birthday", width = 15, format = "yyyy-MM-dd")
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd")
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private Date birthday;

  /** Gender (1: male 2: female) */
  @Excel(name = "Gender", width = 15, dicCode = "sex")
  @Dict(dicCode = "sex")
  private Integer sex;

  /** e-mail */
  @Excel(name = "Email", width = 15)
  private String email;

  /** Email notification (1: enabled 2: disabled) */
  @Dict(dicCode = "status")
  private Integer emailNotice;

  /** Telephone */
  @Excel(name = "Phone", width = 15)
  private String phone;

  /** Log in and select department code */
  private String orgCode;

  /** Log in and select tenant ID */
  private Integer loginTenantId;

  /** DepartmentName */
  private transient String orgCodeTxt;

  /** Status (1: normal 2: frozen) */
  @Excel(name = "Status", width = 15, dicCode = "user_status")
  @Dict(dicCode = "user_status")
  private Integer status;

  /** Whether to allow login (0: not allowed 1: allowed) */
  @Excel(name = "Whether login is allowed", width = 15)
  @Dict(dicCode = "status")
  private Integer allowLogin;

  /** delete status (0, normal, 1 deleted) */
  @Excel(name = "DeleteStatus", width = 15, dicCode = "del_flag")
  @TableLogic
  private Integer delFlag;

  /** Job number, unique key */
  @Excel(name = "Employee ID", width = 15)
  private String workNo;

  /** Position, associated job list */
  @Excel(name = "Job title", width = 15)
  @Dict(dictTable = "sys_position", dicText = "name", dicCode = "id")
  @TableField(exist = false)
  private String post;

  /** Landline number */
  @Excel(name = "Landline number", width = 15)
  private String telephone;

  /** Created by */
  private String createBy;

  /** Create time */
  private Date createTime;

  /** Updated by */
  private String updateBy;

  /** Update time */
  private Date updateTime;

  /** Sync workflow engine 1 sync 0 out of sync */
  private Integer activitiSync;

  /** Identity (0 ordinary member 1 superior) */
  @Excel(name = "(1 regular member 2 superior)", width = 15)
  private Integer userIdentity;

  /** Responsible department */
  @Excel(
      name = "Responsible department",
      width = 15,
      dictTable = "sys_depart",
      dicText = "depart_name",
      dicCode = "id")
  private String departIds;

  /** Multi-tenant ids are used temporarily and are not persisted to the database (database fields do not exist) */
  @TableField(exist = false)
  private String relTenantIds;

  /** Device ID uniapp push use */
  private String clientId;

  /** Login homepage address */
  @TableField(exist = false)
  private String homePath;

  /** Job title */
  @TableField(exist = false)
  private String postText;

  /** process status */
  private String bpmStatus;

  /** Whether the third party has been bound */
  @TableField(exist = false)
  private boolean izBindThird;

  @Dict(dicCode = "user_kyc_status")
  private String kycStatus;

  @TableField(exist = false)
  private String kycStatus_dictText;

  /** Personal Center KYC attachment1 (OSS objectId; the column name has an underscore before the number and needs to be explicitly mapped) */
  @FilePreview
  @TableField(value = "kyc_attachment_1", select = false)
  private String kycAttachment1;

  /** Personal Center KYC attachment2 */
  @FilePreview
  @TableField(value = "kyc_attachment_2", select = false)
  private String kycAttachment2;

  /** Personal Center KYC attachment3 */
  @FilePreview
  @TableField(value = "kyc_attachment_3", select = false)
  private String kycAttachment3;

  /**
   * New KYC module main card business state (12 states): customerType=2 → BUSINESS, otherwise CONSUMER.
   * The list is based on this.
   */
  @TableField(exist = false)
  @Dict(dicCode = "kyc_business_state")
  private String kycBusinessStateCode;

  @TableField(exist = false)
  private String kycBusinessStateCode_dictText;

  /** Corporate card business status (optional disclosure) */
  @TableField(exist = false)
  @Dict(dicCode = "kyc_business_state")
  private String kycTypeBusinessStateCode;

  @TableField(exist = false)
  private String kycTypeBusinessStateCode_dictText;

  /** Personal card business status (optional disclosure) */
  @TableField(exist = false)
  @Dict(dicCode = "kyc_business_state")
  private String kycTypeConsumerStateCode;

  @TableField(exist = false)
  private String kycTypeConsumerStateCode_dictText;

  @Dict(dicCode = "customerType")
  private String customerType;

  @TableField(exist = false)
  private List<String> selectRoleIds;

  @TableField(select = false)
  private String walletAddress;

  /**
   * Account-frozen / KYC-reject reason. Sensitive column — gated by C6 (SensitiveTableBlacklistInterceptor)
   * so it must NEVER be returned by the default SELECT-list (selectById / selectList). Marked
   * {@code select = false} so MyBatis-Plus omits it from auto-generated SELECT SQL; callers that
   * truly need it must explicitly {@code .select(SysUser::getReason)} AND have the request pass
   * C6 (typically via an audited privileged endpoint), otherwise the firewall blocks the query.
   * Updates ({@code .set(SysUser::getReason, ...)}) are unaffected by this flag.
   */
  @TableField(select = false)
  private String reason;

  @TableField(select = false)
  private Date lastPasswordChangeTime;

  @Dict(dicCode = "status")
  @TableField(select = false)
  private String financialState;

  @TableField(exist = false)
  private List<SysRole> roleList;

  @TableField(exist = false)
  private BigDecimal feeRateIncrease;

  @Dict(dictTable = "sys_user", dicText = "username,realname", dicCode = "id", dicTextFormat = "{0}（{1}）")
  private String inviterId;

  private String phoneCode;

  private String idType;

  private String newSubmit;

  private Date lastLoginTime;

  @TableField(exist = false)
  private List<String> labelCodes;

  @TableField(exist = false)
  private List<String> labelTexts;

  @Dict(dictTable = "v_sys_user", dicText = "realname_username", dicCode = "id")
  private String dlygx;

  @TableField(select = false)
  private String isSender;

  @Dict(dicCode = "country_list")
  private String country;

  @TableField(exist = false)
  private Integer beneficiaryCount;

  /** Whether to assign the deposit bank (0: not assigned 1: assigned, from dcs_deposit_bank_user statistics) */
  @TableField(exist = false)
  private Integer depositBankCount;

  /** D4 legacy AES MFA deadline after which old ciphers are rejected */
  @TableField(exist = false)
  @JsonFormat(timezone = "UTC", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date mfaLegacyCutoffAt;

  /** D4 0=legacy AES; 1=MFA_MASTER_SECRET AES-GCM */
  @TableField(exist = false)
  private Integer mfaMasterVersion = 0;

  /**
   * Nullify sensitive fields (password, salt, reason, walletAddress, financialState,
   * kycAttachment1-3, lastPasswordChangeTime, isSender) so this entity can be safely
   * serialized to a JSON response. Called by Controllers before returning to frontend.
   * Idempotent. Returns this for chaining.
   *
   * <p>This is the P0 Controller-layer defense — see CodePlan-SensitiveColumnLeakage-Fix.md
   * §3.1. The Mapper-layer (P1) and entity-layer (P2) defenses are layered on top.
   */
  public SysUser clearSensitiveForResponse() {
    this.password = null;
    this.salt = null;
    this.reason = null;
    this.walletAddress = null;
    this.financialState = null;
    this.kycAttachment1 = null;
    this.kycAttachment2 = null;
    this.kycAttachment3 = null;
    this.lastPasswordChangeTime = null;
    this.isSender = null;
    return this;
  }
}
