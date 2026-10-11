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
    if (userCredentialsMapper == null) {
      return false;
    }

    if (StrUtil.isBlank(userId) || StrUtil.isBlank(paymentPassword)) {
      log.warn("User ID or payment password is empty");
      return false;
    }

    try {
      // Query the caller's own payment password (pinned, owner-scoped statement)
          userCredentialsMapper.selectByUserIdAndType(userId, PAYMENT_PASSWORD_TYPE);
      
      if (userCredentials == null || StrUtil.isBlank(userCredentials.getCredentials())) {
        log.warn("User [{}] payment password not set", userId);
        return false;
      }

      // Verify payment password
      if (!org.jeecg.common.util.CredentialGuard.isPresentLoginPassword(paymentPassword)
          || !org.jeecg.common.util.CredentialGuard.hasStoredPasswordHash(userCredentials.getCredentials())) {
        return false;
      }
      boolean isValid = SmCryptoUtil.checkPassword(paymentPassword, userCredentials.getCredentials());
      
      if (!isValid) {
        log.warn("User [{}] payment password verification failed", userId);
      }
      
      return isValid;
    } catch (Exception e) {
      log.error("Payment password verification exception: {}", e);
      return false;
    }
  }

  /**
   * Check whether user has set payment password
   * 
   * @param userId user id
   * @return whether already set
   */
  public boolean hasPaymentPassword(String userId) {
    if (userCredentialsMapper == null || StrUtil.isBlank(userId)) {
      return false;
    }

    try {
          userCredentialsMapper.selectByUserIdAndType(userId, PAYMENT_PASSWORD_TYPE);
      return userCredentials != null && StrUtil.isNotBlank(userCredentials.getCredentials());
    } catch (Exception e) {
      log.error("Exception checking payment password setup status: {}", e);
      return false;
    }
  }
}

