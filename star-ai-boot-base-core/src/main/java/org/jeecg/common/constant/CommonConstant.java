package org.jeecg.common.constant;

/**
 * @Description: Universal constant
 *
 * @author: jeecg-boot
 */
public interface CommonConstant {

  /** normal state */
  Integer STATUS_NORMAL = 0;

  /** DisableStatus */
  Integer STATUS_DISABLE = -1;

  /** delete flag */
  Integer DEL_FLAG_1 = 1;

  /** not deleted */
  Integer DEL_FLAG_0 = 0;

  /** System logtype: Login */
  int LOG_TYPE_1 = 1;

  /** System logtype: Operation */
  int LOG_TYPE_2 = 2;

  /** System logtype: Tenant operation log */
  int LOG_TYPE_3 = 3;

  /** SystemLogType:  Exception */
  int LOG_TYPE_4 = 4;

  /** Operation logtype: query */
  int OPERATE_TYPE_1 = 1;

  /** Operation logtype: add */
  int OPERATE_TYPE_2 = 2;

  /** Operation logtype: Update */
  int OPERATE_TYPE_3 = 3;

  /** Operation logtype: delete */
  int OPERATE_TYPE_4 = 4;

  /** Operation logtype: Pour */
  int OPERATE_TYPE_5 = 5;

  /** Operation logtype: export */
  int OPERATE_TYPE_6 = 6;

  /** {@code 500 Server Error} (HTTP/1.0 - RFC 1945) */
  Integer SC_INTERNAL_SERVER_ERROR_500 = 500;

  /** {@code 404 Not Found} (HTTP/1.0 - RFC 1945) */
  Integer SC_INTERNAL_NOT_FOUND_404 = 404;

  /** {@code 200 OK} (HTTP/1.0 - RFC 1945) */
  Integer SC_OK_200 = 200;

  /**
   * Second-factor auth required / factor mismatch (not session expiry).
   * Must not reuse 401 — portal treats body code 401 as login redirect unless whitelisted.
   */
  Integer SC_SECOND_AUTH_REQUIRED = 412;

  /** Access permission authentication failed 510 */
  Integer SC_JEECG_NO_AUTHZ = 510;

  /** login userShiro permission cache KEY prefix */
  public static String PREFIX_USER_SHIRO_CACHE =
      "shiro:cache:org.jeecg.config.shiro.ShiroRealm.authorizationCache:";

  /** login userToken token cache KEY prefix */
  String PREFIX_USER_TOKEN = "prefix_user_token:";

  String PREFIX_ACCESS_TOKEN = "prefix_access_token_";

  //    /** Token cache time: 3600 seconds or one hour */
  //    int  TOKEN_EXPIRE_TIME  = 3600;

  /** Login QR code */
  String LOGIN_QRCODE_PRE = "QRCODELOGIN:";

  String LOGIN_QRCODE = "LQ:";

  /** Login QR code token */
  String LOGIN_QRCODE_TOKEN = "LQT:";

  /** 0: First level menu */
  Integer MENU_TYPE_0 = 0;

  /** 1: Submenu */
  Integer MENU_TYPE_1 = 1;

  /** 2: Button permissions */
  Integer MENU_TYPE_2 = 2;

  /** Announcement target type (USER: specific users, ALL: all users) */
  String MSG_TYPE_UESR = "USER";

  String MSG_TYPE_ALL = "ALL";

  /** Publish status (0 unpublished, 1 published, 2 revoked) */
  String NO_SEND = "0";

  String HAS_SEND = "1";
  String HAS_CANCLE = "2";

  /** Read status (0 unread, 1 read) */
  Integer HAS_READ_FLAG = 1;

  Integer NO_READ_FLAG = 0;

  /** Priority (L low, M medium, H high) */
  String PRIORITY_L = "L";

  String PRIORITY_M = "M";
  String PRIORITY_H = "H";

  /** SMS template method 0. Login template, 1. Registration template, 2. Forgot Password template */
  String SMS_TPL_TYPE_0 = "0";

  String SMS_TPL_TYPE_1 = "1";
  String SMS_TPL_TYPE_2 = "2";
  String SMS_TPL_TYPE_3 = "3";

  /** Status (0 invalid 1 valid) */
  String STATUS_0 = "0";

  String STATUS_1 = "1";

  /** Sync workflow engine: 1 sync, 0 do not sync */
  Integer ACT_SYNC_1 = 1;

  Integer ACT_SYNC_0 = 0;

  /** MessageType1:NotificationAnnouncement2:SystemMessage */
  String MSG_CATEGORY_1 = "1";

  String MSG_CATEGORY_2 = "2";

  /** Whether to configure the data permission of the menu 1 yes 0 no */
  Integer RULE_FLAG_0 = 0;

  Integer RULE_FLAG_1 = 1;

  /** Whether the user has been frozen 1 Normal (unfrozen) 2 Freeze 3 Resigned */
  Integer USER_UNFREEZE = 1;

  Integer USER_FREEZE = 2;
  Integer USER_QUIT = 3;

  /** Dictionary translation text suffix */
  String DICT_TEXT_SUFFIX = "_dictText";

  /** Dictionary translation color suffix */
  String DICT_COLOR_SUFFIX = "_dictColor";

  /** Form Designer Main Table Type */
  Integer DESIGN_FORM_TYPE_MAIN = 1;

  /** Form Designer Subform Table Type */
  Integer DESIGN_FORM_TYPE_SUB = 2;

  /** Form designer URL authorization passed */
  Integer DESIGN_FORM_URL_STATUS_PASSED = 1;

  /** Form designer URL authorization failed */
  Integer DESIGN_FORM_URL_STATUS_NOT_PASSED = 2;

  /** Form DesignerCreate Flag */
  String DESIGN_FORM_URL_TYPE_ADD = "add";

  /** Form designer edit Flag */
  String DESIGN_FORM_URL_TYPE_EDIT = "edit";

  /** Form Designer Details Flag */
  String DESIGN_FORM_URL_TYPE_DETAIL = "detail";

  /** Form designer reuses data Flag */
  String DESIGN_FORM_URL_TYPE_REUSE = "reuse";

  /** Form Designer Edit Flag (deprecated) */
  String DESIGN_FORM_URL_TYPE_VIEW = "view";

  /** onlineparams value setting (yes: Y, no: N) */
  String ONLINE_PARAM_VAL_IS_TURE = "Y";

  String ONLINE_PARAM_VAL_IS_FALSE = "N";

  /** File uploadType (local: local, Minio: minio, Alibaba Cloud: alioss) */
  String UPLOAD_TYPE_LOCAL = "local";

  String UPLOAD_TYPE_MINIO = "minio";
  String UPLOAD_TYPE_OSS = "alioss";

  /** Document upload custom bucket name */
  String UPLOAD_CUSTOM_BUCKET = "eoafile";

  /** Document upload custom path */
  String UPLOAD_CUSTOM_PATH = "eoafile";

  /** Number of days the external link to the file is valid */
  Integer UPLOAD_EFFECTIVE_DAYS = 1;

  /** Employee status (1: ordinary employee 2: superior) */
  Integer USER_IDENTITY_1 = 1;

  Integer USER_IDENTITY_2 = 2;

  /** sys_user table username unique key index */
  String SQL_INDEX_UNIQ_SYS_USER_USERNAME = "uniq_sys_user_username";

  /** sys_user table work_no unique key index */
  String SQL_INDEX_UNIQ_SYS_USER_WORK_NO = "uniq_sys_user_work_no";

  /** sys_user table phone unique key index */
  String SQL_INDEX_UNIQ_SYS_USER_PHONE = "uniq_sys_user_phone";

  /** Dameng database upgrade prompt. Violation of uniqueness constraint on table [SYS_USER] */
  String SQL_INDEX_UNIQ_SYS_USER = "Uniqueness constraint";

  /** sys_user table email unique key index */
  String SQL_INDEX_UNIQ_SYS_USER_EMAIL = "uniq_sys_user_email";

  /** sys_quartz_job table job_class_name unique key index */
  String SQL_INDEX_UNIQ_JOB_CLASS_NAME = "uniq_job_class_name";

  /** sys_position table code unique key index */
  String SQL_INDEX_UNIQ_CODE = "uniq_code";

  /** sys_role table code unique key index */
  String SQL_INDEX_UNIQ_SYS_ROLE_CODE = "uniq_sys_role_role_code";

  /** sys_depart table code unique key index */
  String SQL_INDEX_UNIQ_DEPART_ORG_CODE = "uniq_depart_org_code";

  /** sys_category table code unique key index */
  String SQL_INDEX_UNIQ_CATEGORY_CODE = "idx_sc_code";

  /** Is online chat the default group? */
  String IM_DEFAULT_GROUP = "1";

  /** Online chat picture file saving path */
  String IM_UPLOAD_CUSTOM_PATH = "biz/user_imgs";

  /** Online chat user status */
  String IM_STATUS_ONLINE = "online";

  /** Online chat SOCKETMessage type */
  String IM_SOCKET_TYPE = "chatMessage";

  /** Online chat Whether to enable the default add friends 1 Yes 0 No */
  String IM_DEFAULT_ADD_FRIEND = "1";

  /** Online chat user friend cache prefix */
  String IM_PREFIX_USER_FRIEND_CACHE = "sys:cache:im:im_prefix_user_friend_";

  /** Cache the relationship between user id and Username */
  String SYS_USER_ID_MAPPING_CACHE = "sys:cache:user:id_mapping";

  /** Attendance card replacement business status (1: Agree 2: Disagree) */
  String SIGN_PATCH_BIZ_STATUS_1 = "1";

  String SIGN_PATCH_BIZ_STATUS_2 = "2";

  /** Official document upload custom path */
  String UPLOAD_CUSTOM_PATH_OFFICIAL = "officialdoc";

  /** Official document download custom path */
  String DOWNLOAD_CUSTOM_PATH_OFFICIAL = "officaldown";

  /** WPS storage value category (1 code document number 2 text (WPS template or official document sending template)) */
  String WPS_TYPE_1 = "1";

  String WPS_TYPE_2 = "2";

  /**
   * ===============================================================================================
   */
  /**
   * ::Very important:: Notice: If you edit these four constant values, they need to be consistent with jeecg-boot-starter/jeecg-boot-common/org.jeecg.config.FeignConfig
   * Values ​​within classes remain consistent.
   */
  String X_ACCESS_TOKEN = "X-Access-Token";

  String X_SIGN = "X-Sign";
  String X_TIMESTAMP = "X-TIMESTAMP";

  /** Tenant request header renamed: X-Tenant-Id */
  String TENANT_ID = "X-Tenant-Id";

  /** Simplified flow interface request header, used to exclude unsupported control Field */
  String X_MiniFlowExclusionFieldMode = "X-Miniflowexclusionfieldmode";

  /**
   * ===============================================================================================
   */
  String TOKEN_IS_INVALID_MSG = "Token expired; please log in again!";

  String X_FORWARDED_SCHEME = "X-Forwarded-Scheme";

  /** Microservice reads configuration file attribute Service Address */
  String CLOUD_SERVER_KEY = "spring.cloud.nacos.discovery.server-addr";

  /** Third party login, password verification/user creation all need to set an opcode to prevent malicious calls. */
  String THIRD_LOGIN_CODE = "third_login_code";

  /** third partyAPP synchronization direction: local --> third partyAPP */
  String THIRD_SYNC_TO_APP = "SYNC_TO_APP";

  /** third partyAPP synchronization direction: third partyAPP --> local */
  String THIRD_SYNC_TO_LOCAL = "SYNC_TO_LOCAL";

  /** System notification message status: 0=Unpublished */
  String ANNOUNCEMENT_SEND_STATUS_0 = "0";

  /** System notification message status: 1=published */
  String ANNOUNCEMENT_SEND_STATUS_1 = "1";

  /** System notification message status: 2=Cancelled */
  String ANNOUNCEMENT_SEND_STATUS_2 = "2";

  /** ONLINE report permissions are used to obtain the params after the Address column from the request. */
  String ONL_REP_URL_PARAM_STR = "onlRepUrlParamStr";

  /** POST request */
  String HTTP_POST = "POST";

  /** PUT request */
  String HTTP_PUT = "PUT";

  /** PATCH request */
  String HTTP_PATCH = "PATCH";

  /** unknown */
  String UNKNOWN = "unknown";

  /** String http */
  String STR_HTTP = "http";

  /** Null value of String Type */
  String STRING_NULL = "null";

  /** Front-end vue3 version Headerparams name */
  String VERSION = "X-Version";

  String VERSION_V3 = "v3";

  /** Dynamic Table name stored in thread variable */
  String DYNAMIC_TABLE_NAME = "DYNAMIC_TABLE_NAME";

  /** http:// http protocol */
  String HTTP_PROTOCOL = "http://";

  /** https:// https protocol */
  String HTTPS_PROTOCOL = "https://";

  /** Department table unique key, id */
  String DEPART_KEY_ID = "id";

  /** Department table unique key, orgCode */
  String DEPART_KEY_ORG_CODE = "orgCode";

  /**
   * ======[Message push related]================================================================================
   */
  /** Send message will pass some information to the map */
  String NOTICE_MSG_SUMMARY = "NOTICE_MSG_SUMMARY";

  /** Send message will pass a business ID to the map */
  String NOTICE_MSG_BUS_ID = "NOTICE_MSG_BUS_ID";

  /** Send message MessageBusiness type */
  String NOTICE_MSG_BUS_TYPE = "NOTICE_MSG_BUS_TYPE";

  /** The token carried after the Address when logging in to the Mail message needs to be replaced with the real token value. */
  String LOGIN_TOKEN = "{LOGIN_TOKEN}";

  /** The corresponding key of the jump Address in the template message */
  String MSG_HREF_URL = "url";

  /** The Type of the sys_data_log table is used to distinguish the log data in the comment area. */
  String DATA_LOG_TYPE_COMMENT = "comment";

  /** The Type of the sys_data_log table and the old data comparison Type are all set to json. */
  String DATA_LOG_TYPE_JSON = "json";

  /** Message template: markdown */
  String MSG_TEMPLATE_TYPE_MD = "5";

  /** ========[Message push related]============================================================================ */

  /** SMS Verification coderedis-key prefix */
  String PHONE_REDIS_KEY_PRE = "phone_msg";

  /** is a folder */
  String IT_IS_FOLDER = "1";

  /** File owner */
  String FILE_OWNER = "owner";

  /** file manager */
  String FILE_ADMIN = "admin";

  /** Only edit allowed */
  String FILE_EDITABLE = "editable";

  /** Login failure, key used to record the number of failures */
  String LOGIN_FAIL = "LOGIN_FAIL_";

  /** Onboarding events */
  Integer BPM_USER_EVENT_ADD = 1;

  /** Resignation event */
  Integer BPM_USER_EVENT_LEVEL = 2;

  /** User tenant status (normal/approved) */
  String USER_TENANT_NORMAL = "1";

  /** User tenant status (resigned) */
  String USER_TENANT_QUIT = "2";

  /** User tenant status (under review) */
  String USER_TENANT_UNDER_REVIEW = "3";

  /** User tenant status (denied) */
  String USER_TENANT_REFUSE = "4";

  /** User tenant status (invitation) */
  String USER_TENANT_INVITE = "5";

  /** Not a leaf node */
  Integer NOT_LEAF = 0;

  /** is a leaf node */
  Integer IS_LEAF = 1;

  /** DingTalk */
  String DINGTALK = "DINGTALK";

  /** WeCom (Enterprise WeChat) */
  String WECHAT_ENTERPRISE = "WECHAT_ENTERPRISE";

  /** SystemDefaultTenantid 0 */
  Integer TENANT_ID_DEFAULT_VALUE = 0;

  /** [For low-app] Application level replication */
  String COPY_LEVEL_APP = "app";

  /** [For low-app] Menu level copy */
  String COPY_LEVEL_MENU = "menu";

  /** [For low-app] Application backup */
  String COPY_LEVEL_BAK = "backup";

  /** [For low-app] Restore from backup */
  String COPY_LEVEL_COVER = "cover";

  /** [QQYUN-6034] Related Field change history value, cached for half an hour */
  String CACHE_REL_FIELD_OLD_VAL = "sys:cache:desform:relFieldOldVal:";

  /** SortType: ascending order */
  String ORDER_TYPE_ASC = "ASC";

  /** SortType: descending order */
  String ORDER_TYPE_DESC = "DESC";

  /** Calculation formula accuracy (scale during BigDecimal operation, used for calculation of amounts, rates, etc.) */
  int CALCULATION_PRECISION = 10;

  // update-begin---author:scott ---date:2023-09-10 for: Building block report constant----
  /** Reporting allows the role of design and development */
  public static String[] allowDevRoles = new String[] {"lowdeveloper", "admin"};

  /** [Constant corresponding to Building block report] Data isolation mode: Isolate according to creator */
  public static final String SAAS_MODE_CREATED = "created";

  /** [Constant corresponding to Building block report] Data isolation mode: Isolate according to tenant */
  public static final String SAAS_MODE_TENANT = "tenant";

  // update-end---author:scott ---date::2023-09-10 for: Building block report constant----

  // update-begin---author:wangshuai---date:2024-04-07---for:Change mobile number constant---
  /** Change mobile number SMS Verification coderedis-key prefix */
  String CHANGE_PHONE_REDIS_KEY_PRE = "sys:cache:phone:change_phone_msg:";

  /** Cache the last time the user received a message notification KEY */
  String CACHE_KEY_USER_LAST_ANNOUNT_TIME_1HOUR = "sys:cache:userinfo:user_last_annount_time::%s";

  /** Verify original mobile number */
  String VERIFY_ORIGINAL_PHONE = "verifyOriginalPhone";

  /** Change mobile number */
  String UPDATE_PHONE = "updatePhone";

  // update-end---author:wangshuai---date:2024-04-07---for:Change mobile number constant---

  /** Change mobile numberVerification code request times exceeded */
  Integer PHONE_SMS_FAIL_CODE = 40002;

  String EMAIL_REGISTER_REDIS_KEY_PRE = "sys:cache:email:register_email_msg:";
  String EMAIL_CHANGE_PWD_REDIS_KEY_PRE = "sys:cache:email:change_pwd_email_msg:";

  String PREFIX_USER_PASSWORD_LOCK = "prefix_user_password_lock_";

  String PASSWORD_REGEX = "^(?=.*[\\W_])(?=.*[A-Z])(?=.*[a-z])(?=.*\\d{3,}).{12,}$";

  String EMAIL_VERIFICATION_LOCK_KEY = "email_verification_lock_";
  String EMAIL_VERIFICATION_FAIL_COUNT_KEY = "email_verification_fail_count_";

  String EMAIL_SEND_FREQUENCY_KEY = "email_send_frequency_";

  String EMAIL_RESET_PWD_REDIS_KEY_PRE = "sys:cache:email:reset_pwd_email_msg:";

  String EMAIL_LOGIN = "sys:cache:email:login:";

  //Add a secondary authentication key
  String EMAIL_SECOND_AUTH = "sys:cache:email:second_auth:";

  String PREFIX_USER_TOKEN_NAME = "";

  String PREFIX_LAST_USER_TOKEN = "prefix_last_user_token_";

  /** @deprecated please use {@link org.jeecg.common.util.LogoLoader#getLogo()}, supports Nacos dynamic configuration */
  @Deprecated
  String LOGO = org.jeecg.common.util.LogoLoader.DEFAULT_LOGO_URL;
}
