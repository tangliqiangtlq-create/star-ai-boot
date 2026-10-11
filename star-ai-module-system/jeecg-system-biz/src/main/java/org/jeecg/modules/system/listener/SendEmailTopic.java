package org.jeecg.modules.system.listener;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import org.jeecg.common.enem.EmailScene;
import org.jeecg.common.mq.RocketMQMessageListener;
import org.jeecg.common.mq.RocketMQListener;
import org.jeecg.common.security.audit.H4EmailAuditLogger;
import org.jeecg.common.util.LogoLoader;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.security.OpaqueMqCodec;
import org.jeecg.common.util.security.MqSignedUrlFactory;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.service.ISysDictService;
import org.jeecg.modules.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

@Component
@RocketMQMessageListener(
    topic = "sendEmailTopic_topic",
    consumerGroup = "sendEmailTopic_topic_group")
public class SendEmailTopic implements RocketMQListener<JSONObject> {
  private static final Logger log = LoggerFactory.getLogger(SendEmailTopic.class);

  @Value("${spring.mail.fromEmail}")
  private String fromEmail;

  /** Optional; without config, From is email address only */
  @Value("${spring.mail.fromName:}")
  private String fromName;

  /** Optional; without config, skip Reply-To (replies go to From) */
  @Value("${spring.mail.replyTo:}")
  private String replyTo;

  @Resource private JavaMailSender mailSender;

  /**
   * Use the Spring-managed Freemarker configuration (hardened by
   * {@code E4FreemarkerConfigurationHardener}: ALLOWS_NOTHING + APIBuiltinEnabled(false) +
   * E4SafeTemplateLookupStrategy). Its template root is {@code classpath:/templates/}, so
   * {@code getTemplate("xxx.ftl")} resolves the email templates. Do NOT use the shared
   * {@code sharedFreemarkerConfiguration} singleton here: its TemplateLoader is mutated at runtime
   * by FreemarkerParseFactory/SysBaseApiImpl, which would break template lookup.
   */
  @Resource private FreeMarkerConfigurer freeMarkerConfigurer;

  @Autowired private ISysDictService sysDictService;

  @Autowired private ISysUserService sysUserService;

  @Autowired private JeecgBaseConfig jeecgBaseConfig;
  @Autowired private RedisUtil redisUtil;

  private void putBrandInModel(Map<String, Object> model) {
    model.put("logo", LogoLoader.getLogo());
    String brand = jeecgBaseConfig.getBrandName();
    model.put("brandName", brand != null && !brand.isBlank() ? brand : "Mighty Ramp");
    String domain = jeecgBaseConfig.getPublicDomain();
    model.put("publicDomain", domain != null ? domain : "");
    String portalOrigin = "";
    if (jeecgBaseConfig.getPasswordReset() != null) {
      portalOrigin = StrUtil.trim(jeecgBaseConfig.getPasswordReset().getPortalOrigin());
    }
    model.put("portalOrigin", portalOrigin);
    putSupportEmailInModel(model);
  }

  private void putSupportEmailInModel(Map<String, Object> model) {
    String email = jeecgBaseConfig.getContactSupportEmail();
    model.put("supportEmail", email != null ? email : "");
  }

  /** Email subject brand prefix; product currently hides it (always empty). */
  private String brandSubjectPrefix() {
    return "";
  }

  @Override
  public void onMessage(JSONObject data) {
    cn.hutool.json.JSONObject safe = cn.hutool.json.JSONUtil.parseObj(data.toString());
    safe.remove("captcha");
    safe.remove("verificationCode");
    String safeEmail = safe.getStr("email");
    if (safeEmail != null && safeEmail.contains("@")) {
      int at = safeEmail.indexOf('@');
      if (at > 2) {
        safe.set("email", safeEmail.substring(0, 2) + "***" + safeEmail.substring(at));
      } else if (at > 0) {
        safe.set("email", safeEmail.charAt(0) + "***" + safeEmail.substring(at));
      }
    }
    String safeUsername = safe.getStr("username");
    if (safeUsername != null && safeUsername.length() > 3) {
      safe.set("username", safeUsername.substring(0, 1) + "***" + safeUsername.substring(safeUsername.length() - 1));
    }
    log.info("SendEmailTopic received: scene={}, email={}, username={}, keys={}",
        safe.getStr("emailScene"), safe.getStr("email"), safe.getStr("username"), safe.keySet());
    if (data == null) {
      log.error("SendEmailTopic skip: payload is null");
      return;
    }

    String emailSceneStr = data.getStr("emailScene");
    // hutool/Jackson may serialize Enum as object: prefer name, then description
    if (StrUtil.isBlank(emailSceneStr) && data.get("emailScene") != null) {
      Object raw = data.get("emailScene");
      if (raw instanceof EmailScene) {
        emailSceneStr = ((EmailScene) raw).name();
      } else if (raw instanceof JSONObject) {
        JSONObject obj = (JSONObject) raw;
        emailSceneStr =
            StrUtil.blankToDefault(
                obj.getStr("name"),
                StrUtil.blankToDefault(obj.getStr("description"), obj.toString()));
      } else {
        emailSceneStr = String.valueOf(raw);
      }
    }

    EmailScene emailScene = resolveEmailScene(emailSceneStr);
    if (emailScene == null) {
      log.error(
          "SendEmailTopic skip: unknown emailScene='{}'. Check system service has latest EmailScene enum.",
          emailSceneStr);
      return;
    }

    switch (emailScene) {
      case VERIFICATION_CODE:
        sendEmailCode(data);
        break;

      case ACCOUNT_LOCKED:
        sendEmailAccountLocked(data);
        break;

      case ACCOUNT_CREATED:
        sendEmailAccountRegister(data);
        break;

      case SELF_REGISTER_SUCCESS:
        sendEmailSelfRegister(data);
        break;

      case DEPOSIT_RESULT:
      case WITHDRAWAL_RESULT:
      case EXCHANGE_RESULT:
        log.info(
            "SendEmailTopic route {} -> sendGenericTransactionNotify, email={}, transactionId={}",
            emailScene,
            data.getStr("email"),
            StrUtil.blankToDefault(data.getStr("transactionId"), data.getStr("resourceId")));
        sendGenericTransactionNotify(data);
        break;

      case EXCEPTION:
        sendExceptionReport(data);
        break;

      // Ops generic notify + legacy scenario alias compatibility
      case OPS_NOTIFY:
      case PENDING_APPROVAL_ITEM:
      case TRADE_REQUEST_CREATED:
      case PAYOUT_APPROVED:
        sendOpsNotifyEmail(data);
        break;
      default:
        log.error("Email scene not supported: {}", emailScene);
    }
  }

  private static EmailScene resolveEmailScene(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    String key = raw.trim();
    try {
      return EmailScene.valueOf(key);
    } catch (IllegalArgumentException ignored) {
      // fall through
    }
    for (EmailScene scene : EmailScene.values()) {
      if (scene.name().equalsIgnoreCase(key)
          || StrUtil.equalsIgnoreCase(scene.getDescription(), key)) {
        return scene;
      }
    }
    return null;
  }

  /**
   * Ops generic notify: template {@code ops_notify_email.ftl} handles layout only;
   * itemTitle/badgeText/introText/detailText/outroText come from business via MQ.
   */
  private void sendOpsNotifyEmail(JSONObject data) {
    String toEmail = data.getStr("email");
    String title = data.getStr("itemTitle");
    if (StrUtil.isBlank(toEmail) || StrUtil.isBlank(title)) {
      log.error(
          "Skip ops notify email: email/itemTitle blank, to={}, title={}, itemType={}, resourceId={}",
          toEmail,
          title,
          data.getStr("itemType"),
          data.getStr("resourceId"));
      return;
    }

    Map<String, Object> model = new HashMap<>();
    model.put("email", toEmail);
    model.put("itemType", data.getStr("itemType"));
    model.put("itemTitle", title);
    model.put("badgeText", data.getStr("badgeText"));
    model.put("introText", data.getStr("introText"));
    model.put("detailText", data.getStr("detailText"));
    model.put("outroText", data.getStr("outroText"));
    model.put("resourceId", data.getStr("resourceId"));
    model.put("sentDate", DateUtil.formatDateTime(new Date()));
    putBrandInModel(model);
    putDisplayUsername(model, toEmail);

    try {
      Configuration freemarkerConfig = freeMarkerConfigurer.getConfiguration();
      Template template = freemarkerConfig.getTemplate("ops_notify_email.ftl");
      StringWriter writer = new StringWriter();
      template.process(model, writer);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      setToEmails(helper, toEmail);
      String subject = brandSubjectPrefix() + title;
      helper.setSubject(subject);
      helper.setText(writer.toString(), true);
      mailSender.send(mimeMessage);
      log.info(
          "Ops notify email sent: to={}, subject={}, itemType={}, resourceId={}",
          toEmail,
          subject,
          data.getStr("itemType"),
          data.getStr("resourceId"));
    } catch (Exception e) {
      log.error(
          "Error sending ops notify email: to={}, itemType={}, resourceId={}, from={}",
          toEmail,
          data.getStr("itemType"),
          data.getStr("resourceId"),
          fromEmail,
          e);
    }
  }

  /** User transaction-result notify (e.g. deposit): template {@code generic_transaction_notify_email.ftl}. */
  private void sendGenericTransactionNotify(JSONObject data) {
    String toEmail = data.getStr("email");
    String itemTitle = data.getStr("itemTitle");
    String statusTitle = data.getStr("statusTitle");
    String depositStatus = data.getStr("depositStatus");
    String transactionId =
        StrUtil.blankToDefault(data.getStr("transactionId"), data.getStr("resourceId"));

    log.info(
        "generic_transaction_notify start: to={}, itemTitle={}, statusTitle={}, depositStatus={}, transactionId={}, from={}",
        toEmail,
        itemTitle,
        statusTitle,
        depositStatus,
        transactionId,
        fromEmail);

    if (StrUtil.isBlank(toEmail)) {
      log.error(
          "generic_transaction_notify skip: email blank, itemTitle={}, statusTitle={}, depositStatus={}, transactionId={}, payloadKeys={}",
          itemTitle,
          statusTitle,
          depositStatus,
          transactionId,
          data == null ? null : data.keySet());
      return;
    }

    Map<String, Object> model = new HashMap<>();
    model.put("email", toEmail);
    model.put("itemTitle", itemTitle);
    model.put("statusTitle", statusTitle);
    model.put("statusTone", StrUtil.blankToDefault(data.getStr("statusTone"), "neutral"));
    model.put("introText", data.getStr("introText"));
    model.put("detailText", data.getStr("detailText"));
    model.put("outroText", data.getStr("outroText"));
    model.put(
        "showSecurityNotice",
        data.getBool("showSecurityNotice") == null || data.getBool("showSecurityNotice"));
    model.put("sentDate", DateUtil.formatDateTime(new Date()));
    putBrandInModel(model);
    putDisplayUsername(model, toEmail);
    if (StrUtil.isBlank(data.getStr("statusTitle"))
        && StrUtil.isNotBlank(data.getStr("depositStatus"))) {
      boolean ok = "1".equals(data.getStr("depositStatus"));
      model.put("statusTitle", ok ? "Deposit Successful" : "Deposit Failed");
      model.put("statusTone", ok ? "success" : "failure");
      model.put(
          "introText",
          ok
              ? "Your deposit has been successfully processed and the funds are now available in your account."
              : "We were unable to process your deposit request.");
      StringBuilder detail = new StringBuilder();
      detail
          .append(ok ? "Deposit Amount: " : "Attempted Amount: ")
          .append(StrUtil.blankToDefault(data.getStr("depositAmount"), "-"))
          .append(" ")
          .append(StrUtil.blankToDefault(data.getStr("currency"), ""))
          .append("\nTransaction ID: ")
          .append(StrUtil.blankToDefault(data.getStr("transactionId"), "-"))
          .append(ok ? "\nDeposit Date: " : "\nAttempt Date: ")
          .append(StrUtil.blankToDefault(data.getStr("depositDate"), "-"));
      if (!ok && StrUtil.isNotBlank(data.getStr("failureReason"))) {
        detail.append("\nReason: ").append(data.getStr("failureReason"));
      }
      model.put("detailText", detail.toString());
      if (!ok) {
        model.put("outroText", "Please address the issue and try again through your account dashboard.");
      }
      if (StrUtil.isBlank(itemTitle)) {
        itemTitle = "Deposit Notification";
        data.set("itemTitle", itemTitle);
      }
      log.info(
          "generic_transaction_notify applied deposit* fallback: ok={}, amount={} {}, transactionId={}",
          ok,
          data.getStr("depositAmount"),
          data.getStr("currency"),
          data.getStr("transactionId"));
    }

    String subject =
        brandSubjectPrefix()
            + StrUtil.blankToDefault(itemTitle, "Notification");
    try {
      Configuration freemarkerConfig = freeMarkerConfigurer.getConfiguration();
      Template template = freemarkerConfig.getTemplate("generic_transaction_notify_email.ftl");
      StringWriter writer = new StringWriter();
      template.process(model, writer);
      int htmlLen = writer.getBuffer().length();

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(toEmail);
      helper.setSubject(subject);
      helper.setText(writer.toString(), true);
      log.info(
          "generic_transaction_notify sending: to={}, from={}, subject={}, htmlLen={}, statusTitle={}, transactionId={}",
          toEmail,
          fromEmail,
          subject,
          htmlLen,
          model.get("statusTitle"),
          transactionId);
      mailSender.send(mimeMessage);
      log.info(
          "generic_transaction_notify sent: to={}, subject={}, statusTitle={}, transactionId={}",
          toEmail,
          subject,
          model.get("statusTitle"),
          transactionId);
    } catch (Exception e) {
      log.error(
          "generic_transaction_notify failed: to={}, from={}, subject={}, statusTitle={}, depositStatus={}, transactionId={}",
          toEmail,
          fromEmail,
          subject,
          model.get("statusTitle"),
          depositStatus,
          transactionId,
          e);
    }
  }

  // ===== CLASS-LEVEL HARD RULE: NEVER log verificationCode / captcha / code / templateParam =====
  // CI GATE (§7 P2-11): rg CODE-only log statements touching OTP keys must equal 0
  private void sendEmailCode(JSONObject data) {
    Map<String, Object> model = new HashMap<>();
    String email = data.getStr("email");
    String captcha = OpaqueMqCodec.decodeForConsumerOrNull(data);
    if (captcha == null) {
      // Permanent failure for THIS payload (expired transport TTL / redis miss / HMAC mismatch):
      // retrying can never succeed. Ack (no rethrow) instead of burning 3 futile retries and
      // dead-lettering; the user simply re-requests the code. Failure reason is logged by
      // OpaqueMqCodec at WARN ("I1 decode EXPIRED" / "I1 decode REDIS MISS"). (2026-09-30)
      String scene = data.getStr("scene");
      String oldKeyPrefix = null;
      try {
        String o = data.getStr("otpTmpPlainKey");
        if (o != null && o.length() >= 16) oldKeyPrefix = o.substring(0, 16);
      } catch (Exception ignored) {
      }
      log.error(
          "SendEmailTopic render SKIP, no retry: otp opaque decode failed (scene={}, legacyOldKeyPrefix={}); user must re-request the verification code",
          scene,
          oldKeyPrefix);
      return;
    }
    model.put("verificationCode", captcha);
    model.put("sentDate", DateUtil.formatDateTime(new Date()));
    model.put("email", email);
    model.put("username", resolveDisplayUsername(email, data.getStr("username")));
    putBrandInModel(model);

    try {
      Configuration freemarkerConfig = freeMarkerConfigurer.getConfiguration();
      Template template = freemarkerConfig.getTemplate("verification_email.ftl");
      StringWriter writer = new StringWriter();
      template.process(model, writer);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(data.getStr("email"));
      helper.setSubject(
          brandSubjectPrefix()
              + "Verification Code: Please complete the verification process as soon as possible");
      helper.setText(writer.toString(), true);
      mailSender.send(mimeMessage);
    } catch (Exception e) {
      log.error("Error rendering email template", e);
    }
  }

  private void sendEmailAccountLocked(JSONObject data) {
    Map<String, Object> model = new HashMap<>();
    model.put("email", data.getStr("email"));
    model.put("lockedIp", data.getStr("lockedIp"));
    model.put("lockDuration", data.getStr("lockDuration"));
    model.put("sentDate", DateUtil.formatDateTime(new Date()));
    model.put("lockReasonText", data.getStr("lockReasonText"));
    putBrandInModel(model);
    putDisplayUsername(model, data.getStr("email"));

    try {
      Configuration freemarkerConfig = freeMarkerConfigurer.getConfiguration();
      Template template = freemarkerConfig.getTemplate("accountLockedNotification.ftl");
      StringWriter writer = new StringWriter();
      template.process(model, writer);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(data.getStr("email"));
      helper.setSubject(brandSubjectPrefix() + "Account Locked Notification: Immediate Action Required");
      helper.setText(writer.toString(), true);
      mailSender.send(mimeMessage);
    } catch (Exception e) {
      log.error("Error rendering email template", e);
    }
  }

  private void sendEmailAccountRegister(JSONObject data) {
    Map<String, Object> model = new HashMap<>();
    MqSignedUrlFactory.ResolvedLink resolved = MqSignedUrlFactory.resolveAndVerifyLink(data);
    String setPasswordUrl = resolved.setPasswordUrl;
    String linkExpireTime =
        StrUtil.isNotBlank(resolved.linkExpireTime)
            ? resolved.linkExpireTime
            : data.getStr("linkExpireTime");
    boolean inviteLinkEnabled = resolved.inviteLinkEnabled;
    model.put("firstName", data.getStr("firstName"));
    model.put("setPasswordUrl", setPasswordUrl);
    model.put("linkExpireTime", linkExpireTime);
    model.put("inviteLinkEnabled", inviteLinkEnabled);
    model.put("sentDate", DateUtil.formatDateTime(new Date()));
    putBrandInModel(model);
    putDisplayUsername(model, data.getStr("userEmail"), data.getStr("firstName"), data.getStr("username"));

    try {
      Configuration freemarkerConfig = freeMarkerConfigurer.getConfiguration();
      Template template = freemarkerConfig.getTemplate("account_created.ftl");
      StringWriter writer = new StringWriter();
      template.process(model, writer);
      String html = writer.toString();
      int sizeBytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      String subject = brandSubjectPrefix() + "Account created";
      String userEmail = data.getStr("userEmail");
      String messageId = "msg-" + IdUtil.fastSimpleUUID().substring(0, 12);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(userEmail);
      helper.setSubject(subject);
      helper.setText(html, true);
      mailSender.send(mimeMessage);
      H4EmailAuditLogger.logSend(subject, userEmail, sizeBytes, EmailScene.ACCOUNT_CREATED.name(), messageId);
      log.info(
          "ACCOUNT_CREATED email queued: recipientHashPresent={} inviteLinkEnabled={} sizeBytes={}",
          StrUtil.isNotBlank(userEmail) && userEmail.contains("@"),
          inviteLinkEnabled,
          sizeBytes);
    } catch (Exception e) {
      H4EmailAuditLogger.logFailure(
          brandSubjectPrefix() + "Account created",
          data.getStr("userEmail"),
          EmailScene.ACCOUNT_CREATED.name(),
          e.toString());
      log.error("Error rendering account_created email template", e);
    }
  }

  private void sendEmailSelfRegister(JSONObject data) {
    Map<String, Object> model = new HashMap<>();
    model.put("userEmail", data.getStr("userEmail"));
    model.put("sentDate", DateUtil.formatDateTime(new Date()));
    putBrandInModel(model);
    putDisplayUsername(model, data.getStr("userEmail"), data.getStr("username"));

    try {
      Configuration freemarkerConfig = freeMarkerConfigurer.getConfiguration();
      Template template = freemarkerConfig.getTemplate("selfRegisterSuccess.ftl");
      StringWriter writer = new StringWriter();
      template.process(model, writer);

      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(data.getStr("userEmail"));
      helper.setSubject(brandSubjectPrefix() + "Account Registration Successful");
      helper.setText(writer.toString(), true);
      mailSender.send(mimeMessage);
    } catch (Exception e) {
      log.error("Error rendering email template", e);
    }
  }

  /** With spring.mail.fromName use display name; else email only */
  private void applyFrom(MimeMessageHelper helper) throws Exception {
    if (StrUtil.isNotBlank(fromName)) {
      helper.setFrom(fromEmail, fromName.trim());
    } else {
      helper.setFrom(fromEmail);
    }
  }

  /** Set Reply-To only when spring.mail.replyTo configured */
  private void applyReplyTo(MimeMessageHelper helper) throws Exception {
    if (StrUtil.isNotBlank(replyTo)) {
      helper.setReplyTo(replyTo.trim());
    }
  }

  /** Support comma-separated recipients (same as sys_config.dev_email) */
  private void setToEmails(MimeMessageHelper helper, String raw) throws Exception {
    if (StrUtil.isBlank(raw)) {
      throw new IllegalArgumentException("email is blank");
    }
    String[] parts = raw.split("[,;]");
    java.util.List<String> emails = new java.util.ArrayList<>();
    for (String part : parts) {
      String e = part == null ? null : part.trim();
      if (StrUtil.isNotBlank(e)) {
        emails.add(e);
      }
    }
    if (emails.isEmpty()) {
      throw new IllegalArgumentException("email is blank after split");
    }
    helper.setTo(emails.toArray(new String[0]));
  }

  // Add method to send exception-report emails
  private void sendExceptionReport(JSONObject data) {

    String exceptionDetails = data.getStr("exceptionDetails");

    String dev_email = sysDictService.queryDictTextByKey("sys_config", "dev_email");
    if (StrUtil.isBlank(dev_email)) {
      log.warn(
          "Skip exception report email: sys_config.dev_email is blank, detailLen={}",
          exceptionDetails == null ? 0 : exceptionDetails.length());
      return;
    }

    String subject = brandSubjectPrefix() + "System Exception Alert";
    log.info(
        "Sending exception report email: to={}, from={}, subject={}, detailLen={}",
        dev_email,
        fromEmail,
        subject,
        exceptionDetails == null ? 0 : exceptionDetails.length());

    try {
      MimeMessage mimeMessage = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(dev_email.split(","));
      helper.setSubject(subject);
      helper.setText(
          "An exception has occurred in the system:\n\n"
              + exceptionDetails
              + "\n\nPlease investigate.",
          false); // false means send plain text

      mailSender.send(mimeMessage);
      log.info("Exception report email sent successfully: to={}, subject={}", dev_email, subject);

    } catch (Exception e) {
      log.error(
          "Error sending exception report email: to={}, subject={}, detailLen={}",
          dev_email,
          subject,
          exceptionDetails == null ? 0 : exceptionDetails.length(),
          e);
    }
  }

  private void putDisplayUsername(Map<String, Object> model, String email, String... hints) {
    model.put("username", resolveDisplayUsername(email, hints));
  }

  private String resolveDisplayUsername(String email, String... hints) {
    String resolved = "";
    if (hints != null) {
      for (String hint : hints) {
        if (StrUtil.isNotBlank(hint)) {
          resolved = hint.trim();
          break;
        }
      }
    }
    if (StrUtil.isBlank(resolved) && StrUtil.isNotBlank(email) && sysUserService != null) {
      SysUser user = sysUserService.getUserByEmail(email.trim());
      if (user == null) {
        user = sysUserService.getUserByName(email.trim());
      }
      if (user != null) {
        resolved = StrUtil.blankToDefault(user.getUsername(), user.getRealname());
      }
    }
    if (StrUtil.isBlank(resolved)) {
      resolved = StrUtil.blankToDefault(email, "there");
    }
    return resolved.replace("@", "\u200B@\u200B");
  }
}
