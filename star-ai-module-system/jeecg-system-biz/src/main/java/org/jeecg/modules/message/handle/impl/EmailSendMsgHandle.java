package org.jeecg.modules.message.handle.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.IdUtil;
import java.util.List;
import java.util.Set;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.ObjectUtils;
import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.security.audit.H4EmailAuditLogger;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.StaticConfig;
import org.jeecg.modules.message.handle.ISendMsgHandle;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * @Description: Email sending info
 *
 * @author: jeecg-boot
 */
@Component("emailSendMsgHandle")
public class EmailSendMsgHandle implements ISendMsgHandle {
  private static final Logger log = LoggerFactory.getLogger(EmailSendMsgHandle.class);
  static String emailFrom;
  static String emailFromName;
  static String emailReplyTo;

  public static void setEmailFrom(String emailFrom) {
    EmailSendMsgHandle.emailFrom = emailFrom;
  }

  public static void setEmailFromName(String emailFromName) {
    EmailSendMsgHandle.emailFromName = emailFromName;
  }

  public static void setEmailReplyTo(String emailReplyTo) {
    EmailSendMsgHandle.emailReplyTo = emailReplyTo;
  }

  @Autowired SysUserMapper sysUserMapper;

  @Autowired private RedisUtil redisUtil;

  /* API description */
  private static final String realNameExp = "{REALNAME}";

  private void ensureMailFromConfig() {
    if (oConvertUtils.isEmpty(emailFrom) || emailFromName == null || emailReplyTo == null) {
      StaticConfig staticConfig = SpringContextUtils.getBean(StaticConfig.class);
      if (oConvertUtils.isEmpty(emailFrom)) {
        setEmailFrom(staticConfig.getEmailFrom());
      }
      if (emailFromName == null) {
        String name = staticConfig.getEmailFromName();
        setEmailFromName(name == null ? "" : name);
      }
      if (emailReplyTo == null) {
        String rt = staticConfig.getEmailReplyTo();
        setEmailReplyTo(rt == null ? "" : rt);
      }
    }
  }

  private void applyFrom(MimeMessageHelper helper) throws MessagingException {
    try {
      if (oConvertUtils.isNotEmpty(emailFromName)) {
        helper.setFrom(emailFrom, emailFromName.trim());
      } else {
        helper.setFrom(emailFrom);
      }
    } catch (java.io.UnsupportedEncodingException e) {
      throw new MessagingException("Failed to set From with display name", e);
    }
  }

  private void applyReplyTo(MimeMessageHelper helper) throws MessagingException {
    if (oConvertUtils.isNotEmpty(emailReplyTo)) {
      helper.setReplyTo(emailReplyTo.trim());
    }
  }

  @Override
  public void sendMsg(String esReceiver, String esTitle, String esContent) {
    JavaMailSender mailSender = (JavaMailSender) SpringContextUtils.getBean("mailSender");
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = null;
    // update-begin-author:taoyan date:20200811 for: Fetch configuration data
    ensureMailFromConfig();
    // update-end-author:taoyan date:20200811 for: Fetch configuration data
    try {
      helper = new MimeMessageHelper(message, true);
      // Set sender email address
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(esReceiver);
      helper.setSubject(esTitle);
      helper.setText(esContent, true);
      mailSender.send(message);
    } catch (MessagingException e) {
      log.error("operation failed", e);
    }
  }

  @Override
  public void sendMessage(MessageDTO messageDTO) {
    String[] arr = messageDTO.getToUser().split(",");
    LambdaQueryWrapper<SysUser> query =
        new LambdaQueryWrapper<SysUser>().in(SysUser::getUsername, arr);
    List<SysUser> list = sysUserMapper.selectList(query);
    String content = messageDTO.getContent();
    String title = messageDTO.getTitle();
    for (SysUser user : list) {
      String email = user.getEmail();
      if (ObjectUtils.isEmpty(email)) {
        continue;
      }
      content = replaceContent(user, content);
      int sizeBytes = content == null ? 0 : content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      String messageId = "msg-" + IdUtil.fastSimpleUUID().substring(0, 12);
      H4EmailAuditLogger.logSend(title, email, sizeBytes, "sys_message_notify", messageId);
      sendMsg(email, title, content);
    }

    // update-begin-author:taoyan date:2023-6-20 for: QQYUN-5557 [Simple flow] Notification node sends email
    // Form has email field; in flow mail node, recipient cannot select email
    Set<String> toEmailList = messageDTO.getToEmailList();
    if (toEmailList != null && toEmailList.size() > 0) {
      for (String email : toEmailList) {
        if (ObjectUtils.isEmpty(email)) {
          continue;
        }
        int sizeBytes = content == null ? 0 : content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String messageId = "msg-" + IdUtil.fastSimpleUUID().substring(0, 12);
        H4EmailAuditLogger.logSend(title, email, sizeBytes, "sys_message_direct_email", messageId);
        sendMsg(email, title, content);
      }
    }
    // update-end-author:taoyan date:2023-6-20 for: QQYUN-5557 [Simple flow] Notification node sends email
    // Form has email field; in flow mail node, recipient cannot select email

    // Send to CC recipients
    sendMessageToCopyUser(messageDTO);
  }

  /**
   * Send email to CC recipients
   *
   * @param messageDTO
   */
  public void sendMessageToCopyUser(MessageDTO messageDTO) {
    String copyToUser = messageDTO.getCopyToUser();
    if (ObjectUtils.isNotEmpty(copyToUser)) {
      LambdaQueryWrapper<SysUser> query =
          new LambdaQueryWrapper<SysUser>().in(SysUser::getUsername, copyToUser.split(","));
      List<SysUser> list = sysUserMapper.selectList(query);
      String content = messageDTO.getContent();
      String title = messageDTO.getTitle();

      for (SysUser user : list) {
        String email = user.getEmail();
        if (ObjectUtils.isEmpty(email)) {
          continue;
        }
        content = replaceContent(user, content);
        int sizeBytes = content == null ? 0 : content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String messageId = "msg-" + IdUtil.fastSimpleUUID().substring(0, 12);
        H4EmailAuditLogger.logSend(title, email, sizeBytes, "sys_message_cc_user", messageId);
        sendEmail(email, content, title);
      }

      Set<String> ccEmailList = messageDTO.getCcEmailList();
      if (ccEmailList != null && ccEmailList.size() > 0) {
        for (String email : ccEmailList) {
          if (ObjectUtils.isEmpty(email)) {
            continue;
          }
          int sizeBytes = content == null ? 0 : content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
          String messageId = "msg-" + IdUtil.fastSimpleUUID().substring(0, 12);
          H4EmailAuditLogger.logSend(title, email, sizeBytes, "sys_message_cc_email", messageId);
          sendEmail(email, content, title);
        }
      }
    }
  }

  /**
   * Send email to CC recipients (invocation)
   *
   * @param email
   * @param content
   * @param title
   */
  private void sendEmail(String email, String content, String title) {
    JavaMailSender mailSender = (JavaMailSender) SpringContextUtils.getBean("mailSender");
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = null;
    ensureMailFromConfig();
    try {
      helper = new MimeMessageHelper(message, true);
      // Set sender email address
      applyFrom(helper);
      applyReplyTo(helper);
      helper.setTo(email);
      // Set CC recipients
      helper.setCc(email);
      helper.setSubject(title);
      helper.setText(content, true);
      mailSender.send(message);
    } catch (MessagingException e) {
      log.error("operation failed", e);
    }
  }

  // update-end-author:taoyan date:2023-6-20 for: QQYUN-5557 [Simple flow] Notification node sends email
  // Form has email field; in flow mail node, recipient cannot select email

  /**
   * Replace email content variables
   *
   * @param user
   * @param content
   * @return
   */
  private String replaceContent(SysUser user, String content) {
    if (content.indexOf(realNameExp) > 0) {
      content =
          content
              .replace("$" + realNameExp, user.getRealname())
              .replace(realNameExp, user.getRealname());
    }

    return content;
  }
}
