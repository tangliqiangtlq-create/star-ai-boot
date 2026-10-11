package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class OtpCodeGeneratorTest {

  @Test
  void defaultDisallowsFixedCode() {
    StandardEnvironment env = new StandardEnvironment();
    assertFalse(OtpCodeGenerator.isFixedCodeAllowed(env));
    assertNotEquals(OtpCodeGenerator.FIXED_DEV_CODE, OtpCodeGenerator.generateNumeric6(env));
  }

  @Test
  void explicitFlagAllowsFixedCode() {
    StandardEnvironment env = new StandardEnvironment();
    Map<String, Object> props = new HashMap<>();
    props.put("OTP_ALLOW_FIXED_CODE", "true");
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
    assertTrue(OtpCodeGenerator.isFixedCodeAllowed(env));
    assertEquals(OtpCodeGenerator.FIXED_DEV_CODE, OtpCodeGenerator.generateNumeric6(env));
  }
}
