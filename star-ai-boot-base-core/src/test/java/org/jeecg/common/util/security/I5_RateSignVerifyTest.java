package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class I5_RateSignVerifyTest {

  @Test
  void signRateMessage_thenVerifyRoundTrip_passes() {
    JSONObject msg = new JSONObject();
    msg.set("sourceCurrency", "USD");
    msg.set("targetCurrency", "CNY");
    msg.set("rate", new BigDecimal("7.12345678"));
    MqSignedUrlFactory.signRateMessage(msg);
    assertEquals(MqSignedUrlFactory.RATE_ORACLE_OWNER, msg.getStr("owner"));
    assertNotNull(msg.getStr("nonce"));
    assertNotNull(msg.getStr("ts"));
    assertNotNull(msg.getStr("sign"));
    MqSignedUrlFactory.RateVerified v =
        assertDoesNotThrow(() -> MqSignedUrlFactory.verifyRatePayload(msg));
    assertEquals("USD", v.sourceCurrency);
    assertEquals("CNY", v.targetCurrency);
    assertEquals(0, new BigDecimal("7.12345678").compareTo(v.rate));
  }

  @Test
  void verifyRatePayload_badHmac_throwsSign() {
    JSONObject msg = new JSONObject();
    msg.set("sourceCurrency", "USD");
    msg.set("targetCurrency", "CNY");
    msg.set("rate", new BigDecimal("0.1"));
    MqSignedUrlFactory.signRateMessage(msg);
    msg.set("sign", msg.getStr("sign") + "FFFF");
    SecurityException ex =
        assertThrows(SecurityException.class, () -> MqSignedUrlFactory.verifyRatePayload(msg));
    assertTrue(ex.getMessage().contains("I5-SIGN"));
  }

  @Test
  void verifyRatePayload_tsOutsideFiveMinuteSkew_throwsSkew() {
    JSONObject msg = new JSONObject();
    msg.set("sourceCurrency", "BTC");
    msg.set("targetCurrency", "USDT");
    msg.set("rate", new BigDecimal("60000"));
    MqSignedUrlFactory.signRateMessage(msg);
    String staleTs = String.valueOf(System.currentTimeMillis() - 6_000_000L);
    msg.set("ts", staleTs);
    String src = msg.getStr("sourceCurrency");
    String tgt = msg.getStr("targetCurrency");
    java.math.BigDecimal rate = msg.getBigDecimal("rate");
    String nonce = msg.getStr("nonce");
    String owner = msg.getStr("owner");
    String ratePlain = rate.stripTrailingZeros().toPlainString();
    String stalePayload = src + "|" + tgt + "|" + ratePlain + "|" + nonce + "|" + staleTs;
    String staleSign = org.jeecg.common.util.SecureHashUtil.hmacSha256Sign(
        stalePayload,
        org.jeecg.common.util.security.MqSignedUrlFactory.effectiveSignSecret(),
        "UTF-8");
    msg.set("sign", staleSign);
    msg.set("owner", owner);
    SecurityException ex =
        assertThrows(SecurityException.class, () -> MqSignedUrlFactory.verifyRatePayload(msg));
    assertTrue(ex.getMessage().contains("I5-SKEW"), "msg=" + ex.getMessage());
  }

  @Test
  void verifyRatePayload_ownerForged_throwsOwner() {
    JSONObject msg = new JSONObject();
    msg.set("sourceCurrency", "USD");
    msg.set("targetCurrency", "EUR");
    msg.set("rate", new BigDecimal("0.92"));
    MqSignedUrlFactory.signRateMessage(msg);
    msg.set("owner", "HACKER_X");
    String src = msg.getStr("sourceCurrency");
    String tgt = msg.getStr("targetCurrency");
    java.math.BigDecimal rate = msg.getBigDecimal("rate");
    String nonce = msg.getStr("nonce");
    String ts = msg.getStr("ts");
    String ratePlain = rate.stripTrailingZeros().toPlainString();
    String forgedOwnerPayload = src + "|" + tgt + "|" + ratePlain + "|" + nonce + "|" + ts;
    String forgedOwnerSign = org.jeecg.common.util.SecureHashUtil.hmacSha256Sign(
        forgedOwnerPayload,
        org.jeecg.common.util.security.MqSignedUrlFactory.effectiveSignSecret(),
        "UTF-8");
    msg.set("sign", forgedOwnerSign);
    SecurityException ex =
        assertThrows(SecurityException.class, () -> MqSignedUrlFactory.verifyRatePayload(msg));
    assertTrue(ex.getMessage().contains("I5-OWNER"));
  }

  @Test
  void verifyRatePayload_missingFields_throwsSign() {
    SecurityException ex =
        assertThrows(SecurityException.class, () -> MqSignedUrlFactory.verifyRatePayload(new JSONObject()));
    assertTrue(ex.getMessage().contains("missing required field"));
  }
}
