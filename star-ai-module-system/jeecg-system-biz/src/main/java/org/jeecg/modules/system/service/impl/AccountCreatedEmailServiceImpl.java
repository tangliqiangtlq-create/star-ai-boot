package org.jeecg.modules.system.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.enem.EmailScene;
import org.jeecg.common.system.passwordreset.PasswordResetLinkFacade;
import org.jeecg.common.system.passwordreset.exception.PasswordResetErrorCode;
import org.jeecg.common.system.passwordreset.exception.PasswordResetException;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.common.util.RocketMQUtil;
import org.jeecg.common.util.security.MqSignedUrlFactory;
import org.jeecg.modules.system.entity.SysUser;
import org.jeecg.modules.system.service.IAccountCreatedEmailService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Account opening success email (ACCOUNT_CREATED). You must try to bring the password resetUrl; no longer mislead users to go to OTP when the link establishment fails.
 *
 * <p>Link/email must run <strong>async after</strong> transaction submit: if revoke+create runs sync in afterCommit,
 * When the outer transaction has not been cleanup, it is easy to grab the lock with REQUIRES_NEW, resulting in Lock wait timeout and interface timeout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCreatedEmailServiceImpl implements IAccountCreatedEmailService {

  private static final DateTimeFormatter LINK_EXPIRE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
  private static final Executor ASYNC_EXECUTOR =
      new ThreadPoolExecutor(
          2,
          8,
          60L,
          TimeUnit.SECONDS,
          new LinkedBlockingQueue<>(200),
          r -> {
            Thread t = new Thread(r, "account-created-mail-" + THREAD_SEQ.incrementAndGet());
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.CallerRunsPolicy());

  private final PasswordResetLinkFacade passwordResetLinkFacade;
  private final RocketMQUtil rocketMQUtil;

  @Override
  public void scheduleAfterCommit(
      SysUser user, String source, String creatorUserId, String remark) {
    if (user == null || StrUtil.isBlank(user.getEmail())) {
      log.warn(
          "AccountCreatedEmail skip: user/email empty userId={}",
          user == null ? null : user.getId());
      return;
    }
    final String userId = user.getId();
    final String username = user.getUsername();
    final String email = user.getEmail();
    final String firstName = resolveFirstName(user);
    Runnable task = () -> send(userId, username, email, firstName, source, creatorUserId, remark);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              // Only enqueue async work; never do DB/MQ on submit callback thread to avoid lock waits killing HTTP
              ASYNC_EXECUTOR.execute(
                  () -> {
                    try {
                      task.run();
                    } catch (Exception e) {
                      log.error(
                          "AccountCreatedEmail async unexpected userId={} email={}",
                          userId,
                          email,
                          e);
                    }
                  });
            }
          });
    } else {
      ASYNC_EXECUTOR.execute(
          () -> {
            try {
              task.run();
            } catch (Exception e) {
              log.error(
                  "AccountCreatedEmail async unexpected userId={} email={}", userId, email, e);
            }
          });
    }
  }

  private void send(
      String userId,
      String username,
      String email,
      String firstName,
      String source,
      String creatorUserId,
      String remark) {

    // Account opening/invitation: First clear all PENDING in the account to avoid TOO_MANY_PENDING (same as username+source upper limit 3)
    String op = StrUtil.blankToDefault(creatorUserId, "SYS");
    try {
      int n =
          passwordResetLinkFacade.revokeAllPendingByUsername(
              username, op, "ACCOUNT_CREATED before createLink");
      if (n > 0) {
        log.info(
            "AccountCreatedEmail revoked {} pending link(s) username={} before createLink",
            n,
            username);
      }
    } catch (Exception e) {
      log.warn(
          "AccountCreatedEmail revoke pending failed username={}: {}", username);
    }

    PasswordResetLinkVO link = tryCreateLink(userId, username, source, creatorUserId, remark);
    // On source checks failing, fallback retry with OPS_RESET (PENDING cleared; no longer fail for limit)
    if (link == null && !"OPS_RESET".equals(source)) {
      link = tryCreateLink(userId, username, "OPS_RESET", creatorUserId, remark);
    }

    if (link == null || StrUtil.isBlank(link.getResetUrl())) {
      log.error(
          "AccountCreatedEmail WITHOUT resetUrl userId={} username={} email={} — check password-reset config/DB",
          userId,
          username,
          email);
    }
    final boolean hasResetUrl = link != null && StrUtil.isNotBlank(link.getResetUrl());

    JSONObject mq = new JSONObject();
    mq.set("emailScene", EmailScene.ACCOUNT_CREATED.name());
    mq.set("userEmail", email);
    mq.set("firstName", firstName);
    mq.set("username", firstName);
    MqSignedUrlFactory.applyLinkToJson(mq, link);
    try {
      // Send asynchronously without blocking the link building thread
      rocketMQUtil.sendRetry("sendEmailTopic_topic", mq, null);
      log.info(
          "AccountCreatedEmail enqueued userId={} email={} newSignedFormat=true hasResetUrl={} pwResetLinkIdPresent={}",
          userId,
          email,
          hasResetUrl,
          StrUtil.isNotBlank(mq.getStr("pwResetLinkId")));
    } catch (Exception e) {
      log.error("AccountCreatedEmail MQ failed userId={} email={}", userId, email, e);
    }
  }

  private PasswordResetLinkVO tryCreateLink(
      String userId, String username, String source, String creatorUserId, String remark) {
    try {
      return passwordResetLinkFacade.createLink(
          userId,
          username,
          1440,
          5,
          StrUtil.blankToDefault(source, "OPS_RESET"),
          StrUtil.blankToDefault(creatorUserId, "SYS"),
          remark);
    } catch (PasswordResetException e) {
      log.warn(
          "AccountCreatedEmail createLink failed userId={} username={} source={} code={} msg={}",
          userId,
          username,
          source,
          e.getCode());
      if (PasswordResetErrorCode.NOT_PERMITTED.equals(e.getCode())) {
        log.error("password-reset feature disabled (jeecg.password-reset.enabled=false)");
      }
      return null;
    } catch (Exception e) {
      Throwable root = e;
      while (root.getCause() != null && root.getCause() != root) {
        root = root.getCause();
      }
      log.error(
          "AccountCreatedEmail createLink error userId={} username={} source={} rootMsg={}",
          userId,
          username,
          source,
          root.getMessage(),
          e);
      return null;
    }
  }

  private static String resolveFirstName(SysUser user) {
    if (user == null) {
      return "there";
    }
    if (StrUtil.isNotBlank(user.getRealname())) {
      String name = user.getRealname().trim();
      // Avoid lengthy greetings when realname is the entire email address
      if (name.contains("@")) {
        return name.substring(0, name.indexOf('@'));
      }
      int space = name.indexOf(' ');
      return space > 0 ? name.substring(0, space) : name;
    }
    String email = user.getEmail();
    if (StrUtil.isNotBlank(email) && email.contains("@")) {
      return email.substring(0, email.indexOf('@'));
    }
    return "there";
  }
}
