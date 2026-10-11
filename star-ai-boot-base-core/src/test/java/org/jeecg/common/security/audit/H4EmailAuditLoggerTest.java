package org.jeecg.common.security.audit;

import static org.junit.Assert.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class H4EmailAuditLoggerTest {

  private ListAppender<ILoggingEvent> listAppender;
  private Logger auditLogger;

  @Before
  public void setUp() {
    LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
    auditLogger = ctx.getLogger(H4EmailAuditLogger.class);
    listAppender = new ListAppender<>();
    listAppender.setContext(ctx);
    listAppender.start();
    auditLogger.addAppender(listAppender);
  }

  @After
  public void tearDown() {
    if (auditLogger != null && listAppender != null) {
      auditLogger.detachAppender(listAppender);
    }
  }

  @Test
  public void T2_02_logSend_has5Fields_and_maskedRecipient() {
    H4EmailAuditLogger.logSend(
        "Password Reset",
        "alice.smith@example.com",
        2048,
        "TPL_RESET_PWD_01",
        "msg-id-abcdef-123456");

    List<ILoggingEvent> events = listAppender.list;
    assertEquals("H4 logSend must emit exactly 1 event", 1, events.size());
    String msg = events.get(0).getFormattedMessage();

    assertTrue("EMAIL_SENT marker must appear", msg.contains("EMAIL_SENT"));
    assertTrue("subject field must appear with value", msg.contains("subject=Password Reset"));
    assertTrue("size field must appear with 2048 Bytes", msg.contains("size=2048 Bytes"));
    assertTrue("template field must appear", msg.contains("template=TPL_RESET_PWD_01"));
    assertTrue("messageId field must appear", msg.contains("messageId="));

    assertFalse("plain email local-part alice.smith must NOT appear in log",
        msg.contains("alice.smith@"));
    assertFalse("plain email domain example.com full MUST NOT leak",
        msg.contains("alice.smith@example.com"));
    assertTrue("recipient masked format a***@d***.com equivalent must appear (recipient= token)",
        msg.contains("recipient="));
    String recipientToken = extractField(msg, "recipient");
    assertNotNull("recipient field non-null", recipientToken);
    assertTrue("recipient must contain *** email mask", recipientToken.contains("***"));
    assertTrue("recipient mask must contain @", recipientToken.contains("@"));
  }

  @Test
  public void T2_02_resetPasswordUrl_TOKEN_TOTP_neverInAuditLog() {
    H4EmailAuditLogger.logSend(
        "Reset Password https://mpay-stg.xnow.xyz/reset/pwd?token=LEAKEDTOKEN1234567890",
        "bob@corp.example.co.uk",
        9999,
        "TPL_RESET",
        "msg-with-TOKEN=xyz999888 TOTP=654321");

    String msg = listAppender.list.get(0).getFormattedMessage();
    assertFalse("reset/pwd?token= URL plaintext must NOT appear in audit log",
        msg.contains("/reset/pwd?token=LEAKEDTOKEN"));
    assertFalse("TOTP=654321 plaintext 6 digit must NOT appear", msg.contains("TOTP=654321"));
  }

  @Test
  public void T2_02_logFailure_reasonRejectsHttpUrls() {
    H4EmailAuditLogger.logFailure(
        "Account Created",
        "charlie@test.io",
        "TPL_WELCOME",
        "Smtp error; see https://smtp-docs.example.com/err/550?token=SECRET for help");
    String msg = listAppender.list.get(0).getFormattedMessage();
    assertTrue("EMAIL_FAIL marker", msg.contains("EMAIL_FAIL"));
    assertFalse("raw https URL with token=SECRET must be filtered", msg.contains("token=SECRET"));
    assertTrue("URL placeholder <URL> must replace raw URLs", msg.contains("<URL>"));
  }

  private static String extractField(String logMsg, String field) {
    String token = field + "=";
    int i = logMsg.indexOf(token);
    if (i < 0) return null;
    int start = i + token.length();
    int end = logMsg.indexOf(" · ", start);
    if (end < 0) end = logMsg.length();
    return logMsg.substring(start, end);
  }
}
