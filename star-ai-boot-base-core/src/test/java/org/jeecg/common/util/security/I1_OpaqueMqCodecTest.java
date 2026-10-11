package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class I1_OpaqueMqCodecTest {

  @Test
  void sceneNotInWhitelist_throwsIllegalArgument() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OpaqueMqCodec.encodeForProducer("BAD_SCENE_X", "123456", 10));
  }

  @Test
  void encode_producesFourFieldsAndSignMatchesNewFormat() {
    OpaqueMqCodec.OpaqueResult r =
        OpaqueMqCodec.encodeForProducer("TWO_FACTOR_AUTHENTICATION", "777888", 10);
    assertNotNull(r.scene);
    assertNotNull(r.opaqueRef);
    assertTrue(r.expireTs > System.currentTimeMillis() - 1000L);
    assertNotNull(r.sign);
    assertEquals("TWO_FACTOR_AUTHENTICATION", r.scene);
    assertTrue(r.opaqueRef.length() >= 32);
  }

  @Test
  void encode_ttlOverMaximumGetsClampedToMaxTtl() {
    OpaqueMqCodec.OpaqueResult r =
        OpaqueMqCodec.encodeForProducer("LOGIN_OTP", "123456", 86_400);
    long window = r.expireTs - System.currentTimeMillis();
    assertTrue(
        window <= (OpaqueMqCodec.MAX_TTL_SECONDS + 1) * 1000L,
        "TTL clamped to ≤MAX but got window ms = " + window);
    assertTrue(
        window >= (OpaqueMqCodec.MAX_TTL_SECONDS - 1) * 1000L,
        "expireTs in near future");
  }

  @Test
  void encode_ttlNonPositiveUsesConfiguredDefault() {
    // Non-Spring test env: configuredTransportTtlSeconds() falls back to DEFAULT_TTL_SECONDS.
    OpaqueMqCodec.OpaqueResult r = OpaqueMqCodec.encodeForProducer("LOGIN_OTP", "123456", 0);
    long window = r.expireTs - System.currentTimeMillis();
    assertTrue(
        window <= (OpaqueMqCodec.DEFAULT_TTL_SECONDS + 1) * 1000L,
        "default TTL applied, got window ms = " + window);
    assertTrue(
        window >= (OpaqueMqCodec.DEFAULT_TTL_SECONDS - 1) * 1000L,
        "expireTs in near future");
  }

  @Test
  void applyToJson_setsAllFourFieldsCorrectly() {
    OpaqueMqCodec.OpaqueResult r =
        OpaqueMqCodec.encodeForProducer("EMAIL_VERIFY_OTP", "000001", 10);
    JSONObject msg = new JSONObject();
    OpaqueMqCodec.applyToJson(msg, r);
    assertEquals("EMAIL_VERIFY_OTP", msg.getStr("scene"));
    assertEquals(r.opaqueRef, msg.getStr("otpOpaqueRef"));
    assertEquals(String.valueOf(r.expireTs), msg.getStr("otpExpireTs"));
    assertEquals(r.sign, msg.getStr("otpSign"));
    assertNull(msg.getStr("otpTmpPlainKey"), "new format NEVER contains old plain key");
  }

  @Test
  void backwardCompat_returnsNullWhenOldKeyMissingOrBlank() {
    JSONObject data = new JSONObject();
    assertNull(OpaqueMqCodec.backwardCompatReadIfOldFormat(data));
    data.set("otpTmpPlainKey", "");
    assertNull(OpaqueMqCodec.backwardCompatReadIfOldFormat(data));
  }

  @Test
  void encode_ref_32bytes_highEntropy_unique() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      OpaqueMqCodec.OpaqueResult r =
          OpaqueMqCodec.encodeForProducer("TWO_FACTOR_AUTHENTICATION", "123456", 10);
      assertFalse(seen.contains(r.opaqueRef), "opaqueRef collision");
      seen.add(r.opaqueRef);
      assertNotEquals(r.sign, OpaqueMqCodec.encodeForProducer("LOGIN_OTP", "123456", 10).sign);
    }
    assertEquals(100, seen.size());
  }
}
