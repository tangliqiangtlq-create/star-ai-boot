package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.enem.EmailScene;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.config.JeecgBaseConfig;

/**
 * Operation notification email analysis + general operation email.
 *
 * <p>The template only provides layout structure; title, badge, guide, details, closing, etc. <strong>are all passed in by the business initiator</strong>. This tool does not cover business copywriting.
 *
 * <p>Recipient: Dictionary {@code sys_config.ops_notify_email} (supports comma/semicolon/blank separated multiple Mail), fallback if empty
 * contactSupportEmail. 
 */
@Slf4j
public final class OpsNotifyEmailUtil {

  public static final String SYS_CONFIG_DICT_CODE = "sys_config";
  public static final String OPS_NOTIFY_EMAIL_KEY = "ops_notify_email";

  public static final String TYPE_DEPOSIT = "DEPOSIT";
  /** Fiat deposit is pending review (currencyType=1, review page {@code /FiatDepositRecord/DepositRecordList/0}) */
  public static final String TYPE_FIAT_DEPOSIT = "FIAT_DEPOSIT";
  /** Virtual currency deposit pending review (currencyType=2, review page {@code /VirtualDepositRecord/DepositRecordList/0}) */
  public static final String TYPE_VIRTUAL_DEPOSIT = "VIRTUAL_DEPOSIT";
  public static final String TYPE_WITHDRAWAL = "WITHDRAWAL";
  public static final String TYPE_ACCOUNT_OPEN = "ACCOUNT_OPEN";
  public static final String TYPE_DEPOSIT_ACCOUNT = "DEPOSIT_ACCOUNT";
  public static final String TYPE_REMITTANCE = "REMITTANCE";
  public static final String TYPE_BENEFICIARY = "BENEFICIARY";
  public static final String TYPE_TRADE = "TRADE";
  public static final String TYPE_PAYOUT = "PAYOUT";

  private OpsNotifyEmailUtil() {}

  /**
   * Pending email User line: Name + Mail (such as {@code Alice <alice@example.com>}).
   * Username is usually used to log in to Mail; email is given priority, followed by username.
   */
  public static String formatUserDisplay(LoginUser user) {
    if (user == null) {
      return "-";
    }
    String email = StrUtil.blankToDefault(StrUtil.trim(user.getEmail()), StrUtil.trim(user.getUsername()));
    return formatUserDisplay(email, user.getRealname());
  }

  /** @see #formatUserDisplay(LoginUser) */
  public static String formatUserDisplay(String emailOrUsername, String realname) {
    String mail = StrUtil.trim(emailOrUsername);
    String name = StrUtil.trim(realname);
    if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(mail)) {
      return name + " <" + mail + ">";
    }
    if (StrUtil.isNotBlank(mail)) {
      return mail;
    }
    if (StrUtil.isNotBlank(name)) {
      return name;
    }
    return "-";
  }

  /**
   * Standardize multiple emails: split by {@code, ;} or whitespace, remove whitespace and duplication (ignoring case), and splice with English commas.
   */
  public static String normalizeEmails(String raw) {
    if (StrUtil.isBlank(raw)) {
      return "";
    }
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    java.util.List<String> out = new java.util.ArrayList<>();
    for (String part : raw.split("[,;\\s]+")) {
      String email = StrUtil.trim(part);
      if (StrUtil.isBlank(email)) {
        continue;
      }
      String key = email.toLowerCase(java.util.Locale.ROOT);
      if (seen.add(key)) {
        out.add(email);
      }
    }
    return String.join(",", out);
  }

  /**
   * @param fallbackEmail Fallback when the dictionary is empty (usually {@code jeecg.contactSupportEmail})
   */
  public static String resolve(CommonAPI commonAPI, String fallbackEmail) {
    String fromDict = null;
    if (commonAPI != null) {
      try {
        fromDict = commonAPI.translateDict(SYS_CONFIG_DICT_CODE, OPS_NOTIFY_EMAIL_KEY);
      } catch (Exception e) {
        log.warn(
            "Failed to read dict sys_config.ops_notify_email, falling back to contactSupportEmail: {}");
      }
    }
    String normalized = normalizeEmails(StrUtil.blankToDefault(StrUtil.trim(fromDict), ""));
    if (StrUtil.isNotBlank(normalized)) {
      return normalized;
    }
    String fallback = normalizeEmails(StrUtil.trim(fallbackEmail));
    if (StrUtil.isNotBlank(fallback)) {
      log.info(
          "ops_notify_email dictionary is empty, falling back to jeecg.contact-support-email: {}", fallback);
    }
    return fallback;
  }

  /** currencyType=2 is virtual currency, and the rest are considered legal currencies. */
  public static String depositCurrencyTypeLabel(String currencyType) {
    return StrUtil.equals("2", StrUtil.trim(currencyType)) ? "Virtual" : "Fiat";
  }

  public static String depositPendingApprovalItemType(String currencyType) {
    return StrUtil.equals("2", StrUtil.trim(currencyType))
        ? TYPE_VIRTUAL_DEPOSIT
        : TYPE_FIAT_DEPOSIT;
  }

  /**
   * Notify Ops / Payments Mail when the deposit enters pending review (status=0); legal currency and virtual currency are displayed and routed separately.
   *
   * @param amountDetailText multi-line amount block (Deposit Amount / Fee / Actual Amount), aligned with user result email
   */
  public static void notifyDepositPendingApproval(
      CommonAPI commonAPI,
      JeecgBaseConfig config,
      RocketMQUtil rocketMQUtil,
      String currencyType,
      String transactionNo,
      String amountDetailText,
      String userDisplay,
      String resourceId) {
    String ccyTypeLabel = depositCurrencyTypeLabel(currencyType);
    notifyPendingApproval(
        commonAPI,
        config,
        rocketMQUtil,
        depositPendingApprovalItemType(currencyType),
        ccyTypeLabel + " Deposit Pending Approval",
        "Pending Approval",
        "A "
            + ccyTypeLabel.toLowerCase(java.util.Locale.ROOT)
            + " deposit requires review in the operations dashboard.",
        "Transaction No: "
            + StrUtil.blankToDefault(transactionNo, "-")
            + "\n"
            + StrUtil.blankToDefault(amountDetailText, "-")
            + "\nUser: "
            + StrUtil.blankToDefault(userDisplay, "-"),
        "Please review it as soon as possible.",
        resourceId);
  }

  /**
   * Notice of Pending Review (Common Template). The business party needs to pass in all the display copy itself.
   *
   * @param itemType Business type constant (see this class TYPE_*)
   * @param itemTitle Email subject and page Title
   * @param badgeText badge copy (such as Pending Approval)
   * @param introText introductory paragraph before details
   * @param detailText multi-line detail plain text
   * @param outroText The closing paragraph after the details
   * @param resourceId business record ID
   */
  public static void notifyPendingApproval(
      CommonAPI commonAPI,
      JeecgBaseConfig config,
      RocketMQUtil rocketMQUtil,
      String itemType,
      String itemTitle,
      String badgeText,
      String introText,
      String detailText,
      String outroText,
      String resourceId) {
    notifyOps(
        commonAPI,
        config,
        rocketMQUtil,
        itemType,
        itemTitle,
        badgeText,
        introText,
        detailText,
        outroText,
        resourceId);
  }

  /**
   * Universal operational notifications: Unified scenario {@link EmailScene#OPS_NOTIFY} + same Freemarker template.
   * The ContentField is delivered as it is without filling in the business copy by default.
   */
  public static void notifyOps(
      CommonAPI commonAPI,
      JeecgBaseConfig config,
      RocketMQUtil rocketMQUtil,
      String itemType,
      String itemTitle,
      String badgeText,
      String introText,
      String detailText,
      String outroText,
      String resourceId) {
    if (rocketMQUtil == null || config == null) {
      log.error(
          "OPS_NOTIFY not enqueued: rocketMQUtil/config is empty itemType={}, resourceId={}",
          itemType,
          resourceId);
      return;
    }
    String fallback = config.getContactSupportEmail();
    String opsEmail = resolve(commonAPI, fallback);
    if (StrUtil.isBlank(opsEmail)) {
      log.error(
          "OPS_NOTIFY not enqueued: recipient is empty. Configure dict sys_config.ops_notify_email or jeecg.contact-support-email."
              + " itemType={}, resourceId={}, fallbackBlank={}",
          itemType,
          resourceId,
          StrUtil.isBlank(fallback));
      return;
    }
    if (!opsEmail.contains("@")) {
      log.error(
          "OPS_NOTIFY not enqueued: recipient is not a valid email (wrong dict field possible). raw={}, itemType={}, resourceId={}",
          opsEmail,
          itemType,
          resourceId);
      return;
    }
    if (StrUtil.isBlank(itemTitle)) {
      log.error(
          "OPS_NOTIFY not enqueued: itemTitle is empty itemType={}, resourceId={}", itemType, resourceId);
      return;
    }
    try {
      JSONObject message = new JSONObject();
      // The enumeration name string must be used to prevent MQ serialization from writing the Enum as description, causing valueOf to fail on the consumer side.
      message.set("emailScene", EmailScene.OPS_NOTIFY.name());
      message.set("email", opsEmail);
      message.set("itemType", StrUtil.nullToEmpty(itemType));
      message.set("itemTitle", itemTitle.trim());
      message.set("badgeText", StrUtil.nullToEmpty(badgeText));
      message.set("introText", StrUtil.nullToEmpty(introText));
      message.set("detailText", StrUtil.nullToEmpty(detailText));
      message.set("outroText", StrUtil.nullToEmpty(outroText));
      message.set("resourceId", StrUtil.nullToEmpty(resourceId));
      log.info(
          "OPS_NOTIFY enqueued sendEmailTopic_topic: to={}, itemType={}, title={}, resourceId={}",
          opsEmail,
          itemType,
          itemTitle.trim(),
          resourceId);
      rocketMQUtil.sendRetry("sendEmailTopic_topic", message, null);
    } catch (Exception e) {
      log.error(
          "OPS_NOTIFY enqueue exception itemType={}, resourceId={}", itemType, resourceId, e);
    }
  }
}
