package org.jeecg.common.security.audit;

import cn.hutool.core.util.IdUtil;
import org.jeecg.common.constant.HSensitiveRegexConstants;
import org.jeecg.common.security.H7ExceptionRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class H4EmailAuditLogger {

  private static final Logger log = LoggerFactory.getLogger(H4EmailAuditLogger.class);

  private H4EmailAuditLogger() {}

  public static void logSend(
      String subject,
      String recipient,
      int sizeBytes,
      String templateCode,
      String messageId) {
    String preSubject = subject == null ? "" : H7ExceptionRedactor.redactMessageBody(subject);
    String safeSubject = truncate(stripH7Marker(preSubject), 120);
    String recipientHash =
        recipient == null ? "" : HSensitiveRegexConstants.maskEmailLocalPart(recipient);
    String safeTemplate = templateCode == null ? "" : truncate(templateCode, 64);
    String preMsgId =
        messageId == null || messageId.isBlank() ? ("mid-" + IdUtil.fastSimpleUUID().substring(0, 12)) : H7ExceptionRedactor.redactMessageBody(messageId);
    String safeMsgId = truncate(stripH7Marker(preMsgId), 80);
    int safeSize = Math.max(0, sizeBytes);
    log.info(
        "EMAIL_SENT · subject={} · recipient={} · size={} Bytes · template={} · messageId={}",
        safeSubject,
        recipientHash,
        safeSize,
        safeTemplate,
        safeMsgId);
  }

  public static void logFailure(
      String subject,
      String recipient,
      String templateCode,
      String reasonRedacted) {
    String preSubject = subject == null ? "" : H7ExceptionRedactor.redactMessageBody(subject);
    String safeSubject = truncate(stripH7Marker(preSubject), 120);
    String recipientHash =
        recipient == null ? "" : HSensitiveRegexConstants.maskEmailLocalPart(recipient);
    String safeTemplate = templateCode == null ? "" : truncate(templateCode, 64);
    String preReason =
        reasonRedacted == null ? "unknown" : H7ExceptionRedactor.redactMessageBody(reasonRedacted);
    String safeReason = truncate(stripH7Marker(preReason).replaceAll("https?://[^\\s]+", "<URL>"), 160);
    log.warn(
        "EMAIL_FAIL · subject={} · recipient={} · template={} · reason={}",
        safeSubject,
        recipientHash,
        safeTemplate,
        safeReason);
  }

  private static String stripH7Marker(String s) {
    if (s == null) return "";
    return s.replace(H7ExceptionRedactor.REDACTED_MARKER, "").trim();
  }

  private static String truncate(String s, int maxLen) {
    if (s == null) {
      return "";
    }
    if (s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen) + "...";
  }
}
