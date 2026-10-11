package org.jeecg.common.util.security;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.core.env.Environment;

/**
 * B5-3: Fixed OTP {@code 123456} is never enabled by profile alone. Dev/test must set
 * {@code OTP_ALLOW_FIXED_CODE=true} (or {@code mpay.security.otp.allow-fixed-code=true}).
 */
public final class OtpCodeGenerator {

  public static final String FIXED_DEV_CODE = "123456";

  private OtpCodeGenerator() {}

  public static String generateNumeric6(Environment env) {
    if (isFixedCodeAllowed(env)) {
      return FIXED_DEV_CODE;
    }
    return RandomUtil.randomNumbers(6);
  }

  public static boolean isFixedCodeAllowed(Environment env) {
    if (env == null) {
      return false;
    }
    String flag =
        firstNonBlank(
            env.getProperty("OTP_ALLOW_FIXED_CODE"),
            env.getProperty("mpay.security.otp.allow-fixed-code"));
    return "true".equalsIgnoreCase(StrUtil.trim(flag));
  }

  private static String firstNonBlank(String a, String b) {
    if (StrUtil.isNotBlank(a)) {
      return a;
    }
    return b;
  }
}
