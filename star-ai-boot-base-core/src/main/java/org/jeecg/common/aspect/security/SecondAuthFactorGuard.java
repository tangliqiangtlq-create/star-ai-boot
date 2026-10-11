package org.jeecg.common.aspect.security;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.aspect.enumeration.SecondAuthType;

/**
 * B5-1: Bind the second-auth factor type into the Sa-Token session so a weaker factor (e.g. email
 * OTP) cannot satisfy a payment-password-only endpoint after {@code openSafe(service)}.
 */
@Slf4j
public final class SecondAuthFactorGuard {

  public static final String SESSION_KEY_PREFIX = "AUTH_FACTOR:";

  private SecondAuthFactorGuard() {}

  public static void bind(String service, SecondAuthType factor) {
    if (StrUtil.isBlank(service) || factor == null) {
      return;
    }
    StpUtil.getSession().set(SESSION_KEY_PREFIX + service, factor.getCode());
  }

  /**
   * Whether {@code provided} satisfies {@code required} (same rules as endpoint check).
   * Used by openSafeWithAuth so the client cannot bind a weaker factor than the server requires.
   */
  public static boolean satisfies(SecondAuthType required, SecondAuthType provided) {
    if (required == null || provided == null) {
      return false;
    }
    return isCompatible(required, provided.getCode());
  }

  /**
   * @return {@code null} if compatible; otherwise a short reason for the 401 body
   */
  public static String rejectReasonIfIncompatible(String service, SecondAuthType required) {
    if (StrUtil.isBlank(service) || required == null) {
      return null;
    }
    Object raw = StpUtil.getSession().get(SESSION_KEY_PREFIX + service);
    String bound = raw == null ? null : String.valueOf(raw);
    if (StrUtil.isBlank(bound)) {
      log.warn(
          "AUDIT second-auth factor missing: service={} required={}",
          service,
          required.getCode());
      return "Second authentication factor not bound; please call /sys/openSafeWithAuth again";
    }
    if (!isCompatible(required, bound)) {
      log.warn(
          "AUDIT second-auth factor downgrade blocked: service={} required={} bound={}",
          service,
          required.getCode(),
          bound);
      return "Second authentication factor mismatch; required "
          + required.getCode()
          + " but session has "
          + bound
          + "; please call /sys/openSafeWithAuth with the required authType";
    }
    return null;
  }

  static boolean isCompatible(SecondAuthType required, String boundCode) {
    if (required == null || StrUtil.isBlank(boundCode)) {
      return false;
    }
    switch (required) {
      case PAYMENT_PASSWORD_ONLY:
        return SecondAuthType.PAYMENT_PASSWORD_ONLY.getCode().equals(boundCode)
            || SecondAuthType.BOTH_REQUIRED.getCode().equals(boundCode);
      case VERIFICATION_CODE_ONLY:
        return SecondAuthType.VERIFICATION_CODE_ONLY.getCode().equals(boundCode)
            || SecondAuthType.BOTH_REQUIRED.getCode().equals(boundCode)
            || SecondAuthType.EMAIL_PASSWORD_REQUIRED.getCode().equals(boundCode);
      case BOTH_REQUIRED:
        return SecondAuthType.BOTH_REQUIRED.getCode().equals(boundCode);
      case EMAIL_PASSWORD_REQUIRED:
        return SecondAuthType.EMAIL_PASSWORD_REQUIRED.getCode().equals(boundCode);
      default:
        return required.getCode().equals(boundCode);
    }
  }
}
