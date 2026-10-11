package org.jeecg.common.aspect.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.junit.jupiter.api.Test;

class SecondAuthFactorGuardTest {

  @Test
  void emailOtpCannotSatisfyPaymentPasswordOnly() {
    assertFalse(
        SecondAuthFactorGuard.isCompatible(
            SecondAuthType.PAYMENT_PASSWORD_ONLY,
            SecondAuthType.VERIFICATION_CODE_ONLY.getCode()));
  }

  @Test
  void paymentPasswordSatisfiesPaymentPasswordOnly() {
    assertTrue(
        SecondAuthFactorGuard.isCompatible(
            SecondAuthType.PAYMENT_PASSWORD_ONLY,
            SecondAuthType.PAYMENT_PASSWORD_ONLY.getCode()));
  }

  @Test
  void bothRequiredSatisfiesPaymentPasswordOnly() {
    assertTrue(
        SecondAuthFactorGuard.isCompatible(
            SecondAuthType.PAYMENT_PASSWORD_ONLY, SecondAuthType.BOTH_REQUIRED.getCode()));
  }
}
