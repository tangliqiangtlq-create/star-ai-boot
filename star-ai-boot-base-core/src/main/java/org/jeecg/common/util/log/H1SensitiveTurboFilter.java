package org.jeecg.common.util.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Marker;

public class H1SensitiveTurboFilter extends TurboFilter {

  public static final Pattern PATTERN_PASSWORD_KV =
      Pattern.compile("(?i)([\"']?(password|passwd|pwd|secret|client_secret|privateKey|private_key)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{2,})([\"']?)");
  public static final Pattern PATTERN_TOKEN_KV =
      Pattern.compile("(?i)([\"']?(token|access_token|id_token|refresh_token|auth_token|session_token|jwt|bearer)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{4,})([\"']?)");
  public static final Pattern PATTERN_AUTHORIZATION =
      Pattern.compile("(?i)(Authorization:\\s*)(Bearer\\s+|Basic\\s+|Token\\s+)?([A-Za-z0-9._\\-+/=]{8,})");
  public static final Pattern PATTERN_JWT_RAW =
      Pattern.compile("eyJ[a-zA-Z0-9_-]{8,}\\.[a-zA-Z0-9_-]{4,}\\.[a-zA-Z0-9_-]{4,}");
  public static final Pattern PATTERN_OTP_KV =
      Pattern.compile("(?i)([\"']?(otp|totp|mfa|verificationCode|verification_code|captcha|checkCode|check_code|smsCode|sms_code|otpTmpPlainKey)[\"']?\\s*[:=]\\s*)([\"']?)([0-9a-zA-Z]{4,8})([\"']?)");
  public static final Pattern PATTERN_TOTP_RAW =
      Pattern.compile("(?<=\\b)(\\d{6})(?=\\b)");
  public static final Pattern PATTERN_SEED_KV =
      Pattern.compile("(?i)([\"']?(seed|secret_seed|mfa_seed|totp_seed|base32_seed|secretKey|api_secret|apiKey|api_key)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{8,})([\"']?)");
  public static final Pattern PATTERN_SESSIONID_KV =
      Pattern.compile("(?i)([\"']?(sessionId|session_id|JSESSIONID|sid|PHPSESSID)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{8,})([\"']?)");
  public static final Pattern PATTERN_COOKIE_HEADER =
      Pattern.compile("(?i)(Cookie:\\s*|Set-Cookie:\\s*)([^\\r\\n]{8,})");
  public static final Pattern PATTERN_QUERY_TOKEN =
      Pattern.compile("(?i)([?&](token|access_token|id_token|refresh_token|password|pwd|secret|captcha|smsCode|otp|code)=)([^&\\s]{4,})");
  public static final Pattern PATTERN_OAUTH_CODE =
      Pattern.compile("(?i)([\"']?(code|authCode|authorization_code)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{8,})([\"']?)");
  public static final Pattern PATTERN_RESET_URL =
      Pattern.compile("(?i)(https?://[^\\s\"']*?(reset|forgot|recover|pwd|password)[^\\s\"']*?[?&](token|code)=)([^&\\s\"']{4,})");
  public static final Pattern PATTERN_CREDIT_CARD =
      Pattern.compile("\\b(\\d{4})\\d{8,12}(\\d{4})\\b");
  public static final Pattern PATTERN_PHONE_CN =
      Pattern.compile("\\b(1[3-9]\\d)\\d{4}(\\d{4})\\b");
  public static final Pattern PATTERN_EMAIL_ADDR =
      Pattern.compile("([A-Za-z0-9._%+-]{2,})@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
  public static final Pattern PATTERN_BANK_ACCOUNT =
      Pattern.compile("\\b(\\d{4})\\d{8,16}(\\d{4})\\b");
  public static final Pattern PATTERN_SSN_LIKE =
      Pattern.compile("\\b(\\d{3})-?\\d{2}-?(\\d{4})\\b");
  public static final Pattern PATTERN_PRIVATE_KEY_BLOCK =
      Pattern.compile("-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----[\\s\\S]*?-----END (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----");
  public static final Pattern PATTERN_AES_KEY_HEX =
      Pattern.compile("\\b([a-fA-F0-9]{32}|[a-fA-F0-9]{48}|[a-fA-F0-9]{64})\\b");
  public static final Pattern PATTERN_SM4_KEY =
      Pattern.compile("(?i)(sm4:|sm4-)[a-fA-F0-9+/=]{20,}");
  public static final Pattern PATTERN_WS_SECURITY =
      Pattern.compile("(?i)(<wsse:Password[^>]*>)([^<]+)(</wsse:Password>)");
  public static final Pattern PATTERN_SAML_ASSERTION =
      Pattern.compile("(?i)(saml:Assertion[\\s\\S]*?<saml:NameID[^>]*>)([^<]+)(</saml:NameID>)");
  public static final Pattern PATTERN_X_API_KEY =
      Pattern.compile("(?i)(x-api-key:\\s*)([A-Za-z0-9._\\-]{8,})");
  public static final Pattern PATTERN_GATEWAY_SECRET =
      Pattern.compile("(?i)(x-gateway-secret:\\s*|X-Gateway-Secret=)([A-Za-z0-9._\\-]{4,})");
  public static final Pattern PATTERN_WEBHOOK_URL =
      Pattern.compile("(?i)(https?://[^\\s\"']*?/webhook[^\\s\"']*?[?&](secret|token|key|sig|signature)=)([^&\\s\"']{4,})");
  public static final Pattern PATTERN_SIGNATURE_PARAM =
      Pattern.compile("(?i)([\"']?(signature|sign|sig|hmac|digest|mac)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{8,})([\"']?)");
  public static final Pattern PATTERN_NONCE =
      Pattern.compile("(?i)([\"']?(nonce|cipher|iv|salt|initialization_vector)[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{6,})([\"']?)");
  public static final Pattern PATTERN_DB_URL_PASSWORD =
      Pattern.compile("(?i)(jdbc:[a-zA-Z]+://[^\\s\"':]+:)([^\\s\"'@/]+)(@)");
  public static final Pattern PATTERN_REDIS_URL_PASSWORD =
      Pattern.compile("(?i)(redis://[^\\s\"':]+:)([^\\s\"'@/]+)(@)");
  public static final Pattern PATTERN_MONGO_URL_PASSWORD =
      Pattern.compile("(?i)(mongodb(\\+srv)?://[^\\s\"':]+:)([^\\s\"'@/]+)(@)");
  public static final Pattern PATTERN_AMQP_URL_PASSWORD =
      Pattern.compile("(?i)(amqps?://[^\\s\"':]+:)([^\\s\"'@/]+)(@)");

  private static final Pattern[] ALL_PATTERNS = {
      PATTERN_PASSWORD_KV, PATTERN_TOKEN_KV, PATTERN_AUTHORIZATION, PATTERN_JWT_RAW,
      PATTERN_OTP_KV, PATTERN_SEED_KV, PATTERN_SESSIONID_KV, PATTERN_COOKIE_HEADER,
      PATTERN_QUERY_TOKEN, PATTERN_OAUTH_CODE, PATTERN_RESET_URL, PATTERN_CREDIT_CARD,
      PATTERN_PHONE_CN, PATTERN_EMAIL_ADDR, PATTERN_BANK_ACCOUNT, PATTERN_SSN_LIKE,
      PATTERN_PRIVATE_KEY_BLOCK, PATTERN_AES_KEY_HEX, PATTERN_SM4_KEY, PATTERN_WS_SECURITY,
      PATTERN_SAML_ASSERTION, PATTERN_X_API_KEY, PATTERN_GATEWAY_SECRET, PATTERN_WEBHOOK_URL,
      PATTERN_SIGNATURE_PARAM, PATTERN_NONCE, PATTERN_DB_URL_PASSWORD, PATTERN_REDIS_URL_PASSWORD,
      PATTERN_MONGO_URL_PASSWORD, PATTERN_AMQP_URL_PASSWORD, PATTERN_TOTP_RAW
  };

  public static int patternCount() {
    return ALL_PATTERNS.length;
  }

  @Override
  public FilterReply decide(
      Marker marker,
      Logger logger,
      Level level,
      String format,
      Object[] params,
      Throwable t) {
    if (format != null && containsHighRisk(format)) {
      return FilterReply.DENY;
    }
    if (params != null) {
      for (int i = 0; i < params.length; i++) {
        Object p = params[i];
        if (p == null) {
          continue;
        }
        Class<?> pc = p.getClass();
        if (CharSequence.class.isAssignableFrom(pc)) {
          params[i] = redactAll(String.valueOf(p));
        } else if (Throwable.class.isAssignableFrom(pc)) {
          params[i] = redactThrowable((Throwable) p);
        } else if (p instanceof ThrowableProxy tp) {
          params[i] = redactAll(ThrowableProxyUtil.asString(tp));
        }
      }
    }
    if (t != null) {
      redactThrowableStackTraceInPlace(t);
    }
    return FilterReply.NEUTRAL;
  }

  private static boolean containsHighRisk(String s) {
    return PATTERN_PRIVATE_KEY_BLOCK.matcher(s).find()
        || PATTERN_JWT_RAW.matcher(s).find();
  }

  public static String redactAll(String input) {
    if (input == null) {
      return null;
    }
    String out = input;
    out = PATTERN_PRIVATE_KEY_BLOCK.matcher(out).replaceAll("***REDACTED_PRIVATE_KEY***");
    out = PATTERN_JWT_RAW.matcher(out).replaceAll("***");
    out = PATTERN_PASSWORD_KV.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_TOKEN_KV.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_AUTHORIZATION.matcher(out).replaceAll("$1$2***");
    out = PATTERN_OTP_KV.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_SEED_KV.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_SESSIONID_KV.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_COOKIE_HEADER.matcher(out).replaceAll("$1***");
    out = PATTERN_QUERY_TOKEN.matcher(out).replaceAll("$1***");
    out = PATTERN_OAUTH_CODE.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_RESET_URL.matcher(out).replaceAll("$1***");
    out = PATTERN_CREDIT_CARD.matcher(out).replaceAll("$1********$2");
    out = PATTERN_PHONE_CN.matcher(out).replaceAll("$1****$2");
    out = PATTERN_EMAIL_ADDR.matcher(out).replaceAll(mr -> {
      String local = mr.group(1);
      String domain = mr.group(2);
      int localLen = local.length();
      String maskedLocal;
      if (localLen <= 2) {
        maskedLocal = "***";
      } else if (localLen == 3) {
        maskedLocal = local.charAt(0) + "*" + local.charAt(2);
      } else if (localLen == 4) {
        maskedLocal = local.charAt(0) + "**" + local.charAt(3);
      } else {
        maskedLocal = local.substring(0, 2) + "***" + local.substring(localLen - 2);
      }
      String[] domainParts = domain.split("\\.");
      String maskedDomain;
      if (domainParts.length >= 2) {
        String tld = String.join(".", java.util.Arrays.copyOfRange(domainParts, 1, domainParts.length));
        String dm = domainParts[0];
        if (dm.length() <= 2) {
          maskedDomain = "***." + tld;
        } else {
          maskedDomain = dm.charAt(0) + "***" + dm.substring(dm.length() - 1) + "." + tld;
        }
      } else {
        maskedDomain = "***";
      }
      return maskedLocal + "@" + maskedDomain;
    });
    out = PATTERN_BANK_ACCOUNT.matcher(out).replaceAll("$1********$2");
    out = PATTERN_SSN_LIKE.matcher(out).replaceAll("$1-**-$2");
    out = PATTERN_AES_KEY_HEX.matcher(out).replaceAll("***");
    out = PATTERN_SM4_KEY.matcher(out).replaceAll("sm4:***");
    out = PATTERN_WS_SECURITY.matcher(out).replaceAll("$1***$3");
    out = PATTERN_SAML_ASSERTION.matcher(out).replaceAll("$1***$3");
    out = PATTERN_X_API_KEY.matcher(out).replaceAll("$1***");
    out = PATTERN_GATEWAY_SECRET.matcher(out).replaceAll("$1***");
    out = PATTERN_WEBHOOK_URL.matcher(out).replaceAll("$1***");
    out = PATTERN_SIGNATURE_PARAM.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_NONCE.matcher(out).replaceAll("$1$3***$5");
    out = PATTERN_DB_URL_PASSWORD.matcher(out).replaceAll("$1***$3");
    out = PATTERN_REDIS_URL_PASSWORD.matcher(out).replaceAll("$1***$3");
    out = PATTERN_MONGO_URL_PASSWORD.matcher(out).replaceAll("$1***$4");
    out = PATTERN_AMQP_URL_PASSWORD.matcher(out).replaceAll("$1***$3");
    out = PATTERN_TOTP_RAW.matcher(out).replaceAll("******");
    return out;
  }

  public static Throwable redactThrowable(Throwable t) {
    if (t == null) {
      return null;
    }
    String stack = ThrowableProxyUtil.asString(new ThrowableProxy(t));
    String redactedStack = redactAll(stack);
    return new RedactedThrowable(t.getMessage(), t.getCause(), redactedStack);
  }

  private static void redactThrowableStackTraceInPlace(Throwable t) {
    Throwable current = t;
    while (current != null) {
      String originalMsg = current.getMessage();
      if (originalMsg != null) {
        String redactedMsg = redactAll(originalMsg);
        if (!redactedMsg.equals(originalMsg)) {
          try {
            java.lang.reflect.Field detailMessage = Throwable.class.getDeclaredField("detailMessage");
            detailMessage.setAccessible(true);
            detailMessage.set(current, redactedMsg);
          } catch (Throwable ignore) {
          }
        }
      }
      current = current.getCause();
    }
  }

  public static List<String> getPatternNames() {
    List<String> names = new ArrayList<>();
    names.add("PATTERN_PASSWORD_KV");
    names.add("PATTERN_TOKEN_KV");
    names.add("PATTERN_AUTHORIZATION");
    names.add("PATTERN_JWT_RAW");
    names.add("PATTERN_OTP_KV");
    names.add("PATTERN_TOTP_RAW");
    names.add("PATTERN_SEED_KV");
    names.add("PATTERN_SESSIONID_KV");
    names.add("PATTERN_COOKIE_HEADER");
    names.add("PATTERN_QUERY_TOKEN");
    names.add("PATTERN_OAUTH_CODE");
    names.add("PATTERN_RESET_URL");
    names.add("PATTERN_CREDIT_CARD");
    names.add("PATTERN_PHONE_CN");
    names.add("PATTERN_EMAIL_ADDR");
    names.add("PATTERN_BANK_ACCOUNT");
    names.add("PATTERN_SSN_LIKE");
    names.add("PATTERN_PRIVATE_KEY_BLOCK");
    names.add("PATTERN_AES_KEY_HEX");
    names.add("PATTERN_SM4_KEY");
    names.add("PATTERN_WS_SECURITY");
    names.add("PATTERN_SAML_ASSERTION");
    names.add("PATTERN_X_API_KEY");
    names.add("PATTERN_GATEWAY_SECRET");
    names.add("PATTERN_WEBHOOK_URL");
    names.add("PATTERN_SIGNATURE_PARAM");
    names.add("PATTERN_NONCE");
    names.add("PATTERN_DB_URL_PASSWORD");
    names.add("PATTERN_REDIS_URL_PASSWORD");
    names.add("PATTERN_MONGO_URL_PASSWORD");
    names.add("PATTERN_AMQP_URL_PASSWORD");
    return names;
  }

  static class RedactedThrowable extends Throwable {
    private final String redactedStack;

    RedactedThrowable(String msg, Throwable cause, String redactedStack) {
      super(msg, cause);
      this.redactedStack = redactedStack;
    }

    @Override
    public String toString() {
      return redactedStack;
    }
  }
}
