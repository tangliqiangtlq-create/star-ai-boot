package org.jeecg.common.constant;

import java.util.regex.Pattern;

public final class HSensitiveRegexConstants {

  private HSensitiveRegexConstants() {}

  public static final Pattern PATTERN_JWT_TOKEN =
      Pattern.compile(
          "(?i)(?:eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,})");

  public static final Pattern PATTERN_BEARER_TOKEN =
      Pattern.compile("(?i)(bearer\\s+[a-zA-Z0-9._-]{16,})");

  public static final Pattern PATTERN_PASSWORD_FIELD =
      Pattern.compile(
          "(?i)(\"password\"\\s*[:=]\\s*[\"']?[^\"',\\s]{4,}|password\\s*=\\s*[^&,\\s]{4,})");

  public static final Pattern PATTERN_TOTP_DIGITS =
      Pattern.compile(
          "(?:TOTP|otp|verification[_\\-]?code|\u9a8c\u8bc1\u7801)\\s*[:=]?\\s*\\d{4,8}");

  public static final Pattern PATTERN_RESET_URL =
      Pattern.compile(
          "(?i)(https?://[^\\s\"']*(?:reset|forgot|recover|set-password|setPasswordUrl|pwd)[^\\s\"']*)");

  public static final Pattern PATTERN_AUTHORIZATION_HEADER =
      Pattern.compile("(?i)(Authorization:\\s*[^\\r\\n]{8,})");

  public static final Pattern PATTERN_COOKIE_HEADER =
      Pattern.compile("(?i)(Cookie:\\s*[^\\r\\n]{16,}|Set-Cookie:\\s*[^\\r\\n]{16,})");

  public static final Pattern PATTERN_SESSION_ID =
      Pattern.compile(
          "(?i)(session[_-]?id|JSESSIONID|PHPSESSID|sid)\\s*[:=]\\s*[a-zA-Z0-9]{16,}");

  public static final Pattern PATTERN_API_KEY =
      Pattern.compile(
          "(?i)(api[_-]?key|apikey|client[_-]?secret|clientSecret)\\s*[:=]\\s*[\"']?[a-zA-Z0-9_-]{12,}");

  public static final Pattern PATTERN_ACCESS_TOKEN =
      Pattern.compile(
          "(?i)(access[_-]?token|accessToken|refresh[_-]?token|refreshToken|id[_-]?token|idToken)\\s*[:=]\\s*[\"']?[a-zA-Z0-9._-]{10,}");

  public static final Pattern PATTERN_OAUTH_CODE =
      Pattern.compile("(?i)(code\\s*=\\s*[a-zA-Z0-9_-]{10,})");

  public static final Pattern PATTERN_SMS_CODE =
      Pattern.compile(
          "(?:sms|\u77ed\u4fe1|\u77ed\u4fe1\u9a8c\u8bc1|mobile|phone)[^\\r\\n]{0,20}(\\d{4,8})");

  public static final Pattern PATTERN_CREDIT_CARD =
      Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");

  public static final Pattern PATTERN_BANK_ACCOUNT =
      Pattern.compile(
          "(?i)(account[_-]?no|bankAccount|\u5361\u53f7|\u8d26\u53f7)\\s*[:=]\\s*[\"']?\\d{8,}");

  public static final Pattern PATTERN_SSN_NATIONAL_ID =
      Pattern.compile("(?:\\d{3}-\\d{2}-\\d{4}|\\d{17}[\\dXx]|\\d{15})");

  public static final Pattern PATTERN_PHONE_NUMBER =
      Pattern.compile(
          "(?i)(phone|mobile|tel|\u624b\u673a|\u7535\u8bdd)\\s*[:=]\\s*[\"']?(?:\\+?\\d[\\s-]?){7,15}");

  public static final Pattern PATTERN_EMAIL_PLAIN =
      Pattern.compile("[A-Za-z0-9._%+-]{3,}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  public static final Pattern PATTERN_PRIVATE_KEY_HEADER =
      Pattern.compile(
          "(?i)(-----BEGIN (?:RSA |DSA |EC |OPENSSH |PGP )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |DSA |EC |OPENSSH |PGP )?PRIVATE KEY-----)");

  public static final Pattern PATTERN_SM2_SEED =
      Pattern.compile(
          "(?i)(mfa[_-]?seed|totp[_-]?seed|secret|seed|\u79c1\u94a5|\u5bc6\u94a5)\\s*[:=]\\s*[\"']?[A-Za-z0-9+/=]{16,}");

  public static final Pattern PATTERN_DATABASE_URL =
      Pattern.compile(
          "(?i)(jdbc:[a-z]+://[^\\s\"']*[:@][^\\s\"']*[:/][^\\s\"']*)");

  public static final Pattern PATTERN_DATABASE_CREDENTIALS =
      Pattern.compile(
          "(?i)(spring\\.datasource[^\\s]*(?:username|password)|db\\.(?:user|pass|pwd))\\s*[:=]\\s*[^\\s,]{3,}");

  public static final Pattern PATTERN_REDIS_PASSWORD =
      Pattern.compile("(?i)(spring\\.redis\\.password|redis\\.pass)\\s*[:=]\\s*[^\\s,]{3,}");

  public static final Pattern PATTERN_MAIL_BODY_CONTENT =
      Pattern.compile(
          "(?i)(email\\s*content|content\\s*:\\s*<html|getContent\\(\\)|\\.body\\s*=|setPasswordUrl[^\\r\\n]{20,})");

  public static final Pattern PATTERN_JDBC_BIND_PARAM =
      Pattern.compile("(\\?\\s*=\\s*[^,\\)]{4,}|bind\\s*[:=]\\s*[^,\\)]{4,})");

  public static final Pattern PATTERN_X_GATEWAY_SECRET =
      Pattern.compile("(?i)(X-Gateway-Secret:\\s*[^\\r\\n]{8,})");

  public static final Pattern PATTERN_X_OTP_HEADER =
      Pattern.compile("(?i)(X-OTP:\\s*\\d{4,8}|X-MFA:\\s*[^\\r\\n]{8,})");

  public static final Pattern PATTERN_CAPTCHA_FIELD =
      Pattern.compile(
          "(?i)(captcha|verification[_\\-]?code|\u9a8c\u8bc1\u7801|captchaCode)\\s*[:=]\\s*[\"']?[a-zA-Z0-9]{3,}");

  public static final Pattern PATTERN_PAYMENT_CVV =
      Pattern.compile(
          "(?i)(cvv|cvc|card[_-]?code|security[_-]?code|\u5b89\u5168\u7801)\\s*[:=]\\s*\\d{3,4}");

  public static final Pattern PATTERN_ADDRESS_WALLET =
      Pattern.compile(
          "(?i)(wallet[_-]?address|deposit[_-]?address|withdraw[_-]?address|\u6536\u6b3e\u5730\u5740|\u63d0\u5e01\u5730\u5740)\\s*[:=]\\s*[\"']?[13MbcqTx][a-zA-Z0-9]{25,}");

  public static final Pattern PATTERN_TRACE_PARENT =
      Pattern.compile("(?i)(traceparent|trace-id|request-id)\\s*[:=]\\s*[a-f0-9-]{16,}");

  public static final Pattern PATTERN_USER_AGENT_FULL =
      Pattern.compile("(?i)(User-Agent:\\s*[^\\r\\n]{50,})");

  public static final Pattern PATTERN_REFERER_FULL = Pattern.compile("(?i)(Referer:\\s*https?://[^\\r\\n?]{20,}\\?[^\\r\\n]{10,})");

  public static final Pattern[] ALL_PATTERNS =
      new Pattern[] {
        PATTERN_JWT_TOKEN,
        PATTERN_BEARER_TOKEN,
        PATTERN_PASSWORD_FIELD,
        PATTERN_TOTP_DIGITS,
        PATTERN_RESET_URL,
        PATTERN_AUTHORIZATION_HEADER,
        PATTERN_COOKIE_HEADER,
        PATTERN_SESSION_ID,
        PATTERN_API_KEY,
        PATTERN_ACCESS_TOKEN,
        PATTERN_OAUTH_CODE,
        PATTERN_SMS_CODE,
        PATTERN_CREDIT_CARD,
        PATTERN_BANK_ACCOUNT,
        PATTERN_SSN_NATIONAL_ID,
        PATTERN_PHONE_NUMBER,
        PATTERN_EMAIL_PLAIN,
        PATTERN_PRIVATE_KEY_HEADER,
        PATTERN_SM2_SEED,
        PATTERN_DATABASE_URL,
        PATTERN_DATABASE_CREDENTIALS,
        PATTERN_REDIS_PASSWORD,
        PATTERN_MAIL_BODY_CONTENT,
        PATTERN_JDBC_BIND_PARAM,
        PATTERN_X_GATEWAY_SECRET,
        PATTERN_X_OTP_HEADER,
        PATTERN_CAPTCHA_FIELD,
        PATTERN_PAYMENT_CVV,
        PATTERN_ADDRESS_WALLET,
        PATTERN_TRACE_PARENT,
        PATTERN_USER_AGENT_FULL,
        PATTERN_REFERER_FULL
      };

  public static String safeMaskHeadTail(String value, int head, int tail, int maxTotalExposed) {
    if (value == null) {
      return null;
    }
    int len = value.length();
    if (len == 0) {
      return "";
    }
    int totalExposed = Math.min(head + tail, Math.min(maxTotalExposed, len));
    if (totalExposed >= len) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < len; i++) {
        sb.append('*');
      }
      return sb.toString();
    }
    int safeHead = Math.min(head, totalExposed / 2);
    int safeTail = totalExposed - safeHead;
    String prefix = value.substring(0, safeHead);
    String suffix = value.substring(len - safeTail);
    int maskLen = len - safeHead - safeTail;
    StringBuilder sb = new StringBuilder(prefix.length() + maskLen + suffix.length());
    sb.append(prefix);
    for (int i = 0; i < maskLen; i++) {
      sb.append('*');
    }
    sb.append(suffix);
    return sb.toString();
  }

  public static String maskEmailLocalPart(String email) {
    if (email == null || !email.contains("@")) {
      return email;
    }
    int at = email.indexOf('@');
    String local = email.substring(0, at);
    String domain = email.substring(at);
    String maskedLocal;
    if (local.length() <= 1) {
      maskedLocal = "*";
    } else if (local.length() == 2) {
      maskedLocal = local.charAt(0) + "*";
    } else {
      maskedLocal = local.charAt(0) + "***" + local.charAt(local.length() - 1);
    }
    int dot = domain.lastIndexOf('.');
    String maskedDomain;
    String pureDomain = domain.startsWith("@") ? domain.substring(1) : domain;
    int dot2 = pureDomain.lastIndexOf('.');
    if (dot2 > 1) {
      String domainName = pureDomain.substring(0, dot2);
      String tld = pureDomain.substring(dot2);
      if (domainName.length() <= 2) {
        maskedDomain = "*" + tld;
      } else {
        maskedDomain = domainName.charAt(0) + "***" + domainName.charAt(domainName.length() - 1) + tld;
      }
    } else {
      maskedDomain = "***" + pureDomain;
    }
    return maskedLocal + "@" + maskedDomain;
  }
}
