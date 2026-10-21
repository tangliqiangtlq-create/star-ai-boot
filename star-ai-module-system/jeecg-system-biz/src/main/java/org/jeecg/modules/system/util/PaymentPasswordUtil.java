package org.jeecg.modules.system.util;

import cn.hutool.core.util.StrUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Payment password utility
 * 
 * @author jeecg-boot
 */
@Slf4j
@Component
public class PaymentPasswordUtil {

  /** Credential type stored in {@code sys_user_credentials.type}. */
  public static final String PAYMENT_PASSWORD_TYPE = "PAYMENT_PASSWORD";

  /**
   * MyBatis-Plus service, whose wrapper-based MappedStatements cannot be attributed to an owner and
   * are therefore blocked by the A3-4 stage-1 gate for non-admin sessions.
   */
  /**
   * Verify payment password
   * 
   * @param userId user id
   * @param paymentPassword payment password (plaintext)
   * @return whether verification passed
   */
  public boolean verifyPaymentPassword(String userId, String paymentPassword) {
    return false;
  }

  /**
   * Check whether user has set payment password
   * 
   * @param userId user id
   * @return whether already set
   */
  public boolean hasPaymentPassword(String userId) {
    return false;
  }
}

