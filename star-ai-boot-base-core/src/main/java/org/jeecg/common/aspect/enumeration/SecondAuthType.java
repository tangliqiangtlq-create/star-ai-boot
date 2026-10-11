package org.jeecg.common.aspect.enumeration;

import lombok.Getter;

/**
 * second-factor authtype enumeration
 * 
 * @author jeecg-boot
 */
@Getter
public enum SecondAuthType {
  /**
   * Only verification code authentication
   */
  VERIFICATION_CODE_ONLY("VERIFICATION_CODE_ONLY", "Verification code authentication only"),
  
  /**
   * Pay password authentication only
   */
  PAYMENT_PASSWORD_ONLY("PAYMENT_PASSWORD_ONLY", "Payment password authentication only"),
  
  /**
   * Both captcha and payment password required
   */
  BOTH_REQUIRED("BOTH_REQUIRED", "Both verification code and payment password are required"),
  
  /**
   * Both email captcha and login password required
   */
  EMAIL_PASSWORD_REQUIRED("EMAIL_PASSWORD_REQUIRED", "Both email verification code and login password are required");

  private final String code;
  private final String description;

  SecondAuthType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Get enumeration based on code
   */
  public static SecondAuthType fromCode(String code) {
    for (SecondAuthType type : values()) {
      if (type.getCode().equals(code)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown second auth type: " + code);
  }
}

