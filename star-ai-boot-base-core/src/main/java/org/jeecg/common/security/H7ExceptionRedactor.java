package org.jeecg.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class H7ExceptionRedactor {

  public static final String REDACTED_MARKER = "[H7_REDACTED]";
  public static final String REDACTED_VALUE = "***";
  private static final int MAX_CAUSE_DEPTH = 5;
  private static final int MAX_TOTAL_LENGTH = 1000;
  private static final int MAX_MESSAGE_LENGTH = 200;

  private static final Pattern[] SENSITIVE_PATTERNS = {
    Pattern.compile("(?i)\"?\\s*(token|access_token|refresh_token|id_token|sessionid|session_id|jti)\\s*\"?\\s*[=:]\\s*[\"']?[A-Za-z0-9_\\-\\.]{6,}"),
    Pattern.compile("(?i)\"?\\s*(password|passwd|pwd|secret|credential|apikey|api_key|privatekey|private_key)\\s*\"?\\s*[=:]\\s*[\"']?[^\"',\\s]{4,}"),
    Pattern.compile("(?i)\"?\\s*(seed|mfa_seed|hmac|signature|signingkey|signing_key|mastersecret|master_secret)\\s*\"?\\s*[=:]\\s*[\"']?[^\"',\\s]{4,}"),
    Pattern.compile("(?i)\"?\\s*(authorization|cookie|set-cookie)\\s*\"?\\s*[:=]\\s*[\"']?[^\"',\\s]{10,}"),
    Pattern.compile("(?i)\"?\\s*(otp|totp|hotp|captcha|smscode|sms_code|emailcode|email_code)\\s*\"?\\s*[=:]\\s*[\"']?\\d{4,}"),
    Pattern.compile("(?i)(otpauth|totpauth)://[^\\s\"']+"),
    Pattern.compile("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"),
    Pattern.compile("(?i)\"?\\s*(reset_url|resetPasswordUrl|setPasswordUrl)\\s*\"?\\s*[=:]?\\s*[\"']?https?://[^\\s\"']+"),
    Pattern.compile("(?i)/reset/pwd\\?token=[^\\s\"']+"),
    Pattern.compile("(?i)\"?\\s*(salt|iv|nonce)\\s*\"?\\s*[=:]\\s*[\"']?[A-Fa-f0-9]{8,}"),
    Pattern.compile("(?i)\"?\\s*(iban|cvv|cvc|bankaccount|bank_account|cardnumber|card_number|pan|pin)\\s*\"?\\s*[=:]\\s*[\"']?[^\"',\\s]{4,}"),
    Pattern.compile("(?i)\"?\\s*TOTP\\s*\"?\\s*[:=]?\\s*[\"']?\\d{4,8}"),
    Pattern.compile("(?i)reset_password\\?token=[^\\s\"']+")
  };

  private static final Pattern[] LOCAL_PATH_PATTERNS = {
    Pattern.compile("file:/?tmp/[^\\s)'\"]+"),
    Pattern.compile("file:/?private/[^\\s)'\"]+"),
    Pattern.compile("file:/?var/folders/[^\\s)'\"]+"),
    Pattern.compile("(/tmp|/private/tmp|/private/var|/var/folders)/[^\\s)'\":]+"),
    Pattern.compile("(/Users|/home|/root|/opt|/usr/local)/[^\\s):'\"]+(?:\\.java:\\d+|/[^\\s):'\"]*)"),
    Pattern.compile("[A-Z]:\\\\[^\\s):'\"]+(?:\\.java:\\d+|\\\\[^\\s):'\"]*)"),
    Pattern.compile("<generated>"),
    Pattern.compile("\\$\\$FastClassBySpringCGLIB\\$\\$[a-f0-9]+"),
    Pattern.compile("\\$\\$EnhancerBySpringCGLIB\\$\\$[a-f0-9]+")
  };

  private H7ExceptionRedactor() {}

  public static String redactSummary(Throwable thrown) {
    if (thrown == null) {
      return REDACTED_MARKER;
    }
    try {
      StringBuilder sb = new StringBuilder();
      List<Throwable> chain = collectCauseChain(thrown, MAX_CAUSE_DEPTH);
      for (int i = 0; i < chain.size(); i++) {
        Throwable t = chain.get(i);
        if (i > 0) {
          sb.append(" | Caused by: ");
        }
        String className = safeClassName(t);
        sb.append(className);
        String rawMsg = t.getMessage();
        if (rawMsg != null && !rawMsg.isEmpty()) {
          String trimmed = rawMsg.length() > MAX_MESSAGE_LENGTH
              ? rawMsg.substring(0, MAX_MESSAGE_LENGTH)
              : rawMsg;
          String redactedMsg = applyPatterns(trimmed, SENSITIVE_PATTERNS, true);
          sb.append(": ").append(redactedMsg);
        }
        StackTraceElement[] stack = t.getStackTrace();
        if (stack != null && stack.length > 0) {
          int limit = Math.min(3, stack.length);
          sb.append(" | stack[0..").append(limit - 1).append("]=");
          for (int s = 0; s < limit; s++) {
            if (s > 0) sb.append(";");
            String line = renderStackTraceElement(stack[s]);
            sb.append(line);
          }
        }
        if (sb.length() > MAX_TOTAL_LENGTH) {
          sb.setLength(MAX_TOTAL_LENGTH);
          sb.append("...(truncated)");
          break;
        }
      }
      String result = sb.toString();
      if (result.isEmpty()) {
        return REDACTED_MARKER;
      }
      result = applyPatterns(result, SENSITIVE_PATTERNS, true);
      if (!result.contains(REDACTED_MARKER)) {
        result = result + " " + REDACTED_MARKER;
      }
      return result;
    } catch (Throwable safety) {
      return REDACTED_MARKER;
    }
  }

  public static String redactMessageBody(String rawBody) {
    if (rawBody == null || rawBody.isEmpty()) {
      return REDACTED_MARKER;
    }
    try {
      String trimmed = rawBody.length() > MAX_MESSAGE_LENGTH * 2
          ? rawBody.substring(0, MAX_MESSAGE_LENGTH * 2)
          : rawBody;
      String redacted = applyPatterns(trimmed, SENSITIVE_PATTERNS, true);
      return redacted + " " + REDACTED_MARKER;
    } catch (Throwable safety) {
      return REDACTED_MARKER;
    }
  }

  static List<Throwable> collectCauseChain(Throwable root, int maxDepth) {
    List<Throwable> chain = new ArrayList<>(maxDepth);
    Throwable current = root;
    int depth = 0;
    while (current != null && depth < maxDepth) {
      chain.add(current);
      Throwable next = current.getCause();
      if (next == current) {
        break;
      }
      current = next;
      depth++;
    }
    return chain;
  }

  private static String safeClassName(Throwable t) {
    try {
      Class<?> c = t.getClass();
      String simple = c.getSimpleName();
      if (simple != null && !simple.isEmpty()) {
        return simple;
      }
      String full = c.getName();
      if (full != null) {
        int dot = full.lastIndexOf('.');
        return dot >= 0 ? full.substring(dot + 1) : full;
      }
      return "Throwable";
    } catch (Throwable ignore) {
      return "Throwable";
    }
  }

  private static String renderStackTraceElement(StackTraceElement e) {
    if (e == null) {
      return "null";
    }
    try {
      String cls = e.getClassName() == null ? "?" : e.getClassName();
      String method = e.getMethodName() == null ? "?" : e.getMethodName();
      String file = e.getFileName();
      int line = e.getLineNumber();
      String raw;
      if (file != null && line >= 0) {
        raw = cls + "." + method + "(" + file + ":" + line + ")";
      } else if (file != null) {
        raw = cls + "." + method + "(" + file + ")";
      } else {
        raw = cls + "." + method + "(Unknown Source)";
      }
      return applyPatterns(raw, LOCAL_PATH_PATTERNS, false);
    } catch (Throwable ignore) {
      return "?";
    }
  }

  private static String applyPatterns(String input, Pattern[] patterns, boolean withMask) {
    String result = input;
    for (Pattern p : patterns) {
      try {
        result = p.matcher(result).replaceAll(withMask ? REDACTED_VALUE : "[filtered]");
      } catch (Throwable ignore) {
      }
    }
    return result;
  }

  static int sensitivePatternCount() {
    return SENSITIVE_PATTERNS.length;
  }

  static int localPathPatternCount() {
    return LOCAL_PATH_PATTERNS.length;
  }
}
