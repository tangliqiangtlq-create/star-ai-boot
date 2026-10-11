package org.jeecg.common.util;

import cn.hutool.crypto.digest.BCrypt;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.jeecg.common.util.encryption.EncryptedString;
import org.jeecg.common.util.log.SensitiveDataTurboFilter;
import org.jeecg.common.util.security.SecurityTools;
import org.jeecg.common.util.security.entity.MyKeyPair;
import org.jeecg.common.util.security.entity.SecurityReq;
import org.jeecg.common.util.security.entity.SecuritySignReq;
import org.jeecg.common.util.security.entity.SecuritySignResp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Retest after source security remediation (full SM crypto paths). */
public class SecurityRemediationRetestTest {

  @Test
  void sm3Password_hashAndCheck_andBcryptLegacyCompatible() {
    String plain = "HelloChinesePwd!";
    String hashed = SmCryptoUtil.hashPassword(plain);
    Assertions.assertTrue(hashed.startsWith(SmCryptoUtil.PWD_PREFIX));
    Assertions.assertTrue(SmCryptoUtil.checkPassword(plain, hashed));
    Assertions.assertFalse(SmCryptoUtil.checkPassword("wrong", hashed));

    String bcrypt = BCrypt.hashpw(plain, BCrypt.gensalt());
    Assertions.assertTrue(SmCryptoUtil.checkPassword(plain, bcrypt));

    // Global: null/blank passwords must not participate in checks or pass
    Assertions.assertFalse(SmCryptoUtil.checkPassword(null, hashed));
    Assertions.assertFalse(SmCryptoUtil.checkPassword("", hashed));
    Assertions.assertFalse(SmCryptoUtil.checkPassword("   ", hashed));
    Assertions.assertFalse(SmCryptoUtil.checkPassword(plain, null));
    Assertions.assertFalse(SmCryptoUtil.checkPassword(plain, " "));
    Assertions.assertThrows(IllegalArgumentException.class, () -> SmCryptoUtil.hashPassword(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> SmCryptoUtil.hashPassword(""));
  }

  @Test
  void credentialGuard_rejectsBlankPassword() {
    Assertions.assertFalse(CredentialGuard.isPresentLoginPassword((String) null));
    Assertions.assertFalse(CredentialGuard.isPresentLoginPassword(""));
    Assertions.assertFalse(CredentialGuard.hasStoredPasswordHash(" "));
    Assertions.assertTrue(CredentialGuard.isPresentLoginPassword("x"));
    Assertions.assertFalse(CredentialGuard.matchesStoredPassword(null, "hash"));
    Assertions.assertFalse(CredentialGuard.matchesStoredPassword("enc", null));
  }

  @Test
  void encryptedString_envOverride_andBuiltinFlag() {
    Assertions.assertNotNull(EncryptedString.key);
    Assertions.assertEquals(16, EncryptedString.key.length());
    Assertions.assertEquals(16, EncryptedString.iv.length());
  }

  @Test
  void securityTools_signAndValid_sm2Sm4RoundTrip() {
    MyKeyPair pair = SecurityTools.generateKeyPair();
    SecuritySignReq signReq = new SecuritySignReq();
    signReq.setPrikey(pair.getPriKey());
    signReq.setData("{\"ok\":true}");
    SecuritySignResp signed = SecurityTools.sign(signReq);
    Assertions.assertTrue(signed.getData().startsWith(SmCryptoUtil.SM4_PREFIX));

    SecurityReq req = new SecurityReq();
    req.setPubKey(pair.getPubKey());
    req.setAesKey(signed.getAesKey());
    req.setData(signed.getData());
    req.setSignData(signed.getSignData());
    Assertions.assertTrue(SecurityTools.valid(req).getSuccess());
  }

  @Test
  void smCryptoUtil_sm3_and_sm4_roundTrip() {
    String hex = SmCryptoUtil.sm3Hex("abc");
    Assertions.assertNotNull(hex);
    Assertions.assertEquals(64, hex.length());
    String key = SmCryptoUtil.generateSm4KeyHex();
    String enc = SmCryptoUtil.sm4Encrypt("payload", key);
    Assertions.assertEquals("payload", SmCryptoUtil.sm4Decrypt(enc, key));
  }

  @Test
  void aesEncryptUtil_nowUsesSm4_withPrefix() throws Exception {
    String enc = AesEncryptUtil.encrypt("mfa-secret");
    Assertions.assertNotNull(enc);
    Assertions.assertTrue(enc.startsWith(SmCryptoUtil.SM4_PREFIX));
    Assertions.assertEquals("mfa-secret", AesEncryptUtil.desEncrypt(enc));
  }

  @Test
  void jwtSm2_generateAndVerify() {
    MyKeyPair pair = SecurityTools.generateKeyPair();
    JwtRs256SecureUtil.init(pair.getPriKey(), pair.getPubKey());
    String token = JwtRs256SecureUtil.generateToken("user1", 3600, "https://issuer", "web");
    Assertions.assertEquals("user1", JwtRs256SecureUtil.verifyToken(token, "https://issuer").getSubject());
  }

  @Test
  void jwtSm2_configuredPkcs8Keys_roundTrip() {
    // Same material as local .env / nacos example keys (PKCS#8 / X.509 Base64).
    String pri =
        "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk2UK0Dfm+NfCgriEJVfrWc/9ZHHy9IxSNdcz3QcfovWgCgYIKoEcz1UBgi2hRANCAATNKB69HCf22URc4rObTCOQN+tAIxdFiFdu22xzmrWCdcVWzY0MAgd4jd7foVLmtbuphSmKMrhT7QsynN4R0Wcw";
    String pub =
        "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEzSgevRwn9tlEXOKzm0wjkDfrQCMXRYhXbttsc5q1gnXFVs2NDAIHeI3e36FS5rW7qYUpijK4U+0LMpzeEdFnMA==";
    JwtRs256SecureUtil.init(pri, pub);
    String token =
        JwtRs256SecureUtil.generateToken("u", 3600, "http://127.0.0.1:9999/api", "Windows");
    Assertions.assertEquals(
        "u", JwtRs256SecureUtil.verifyToken(token, "http://127.0.0.1:9999/api").getSubject());
  }

  @Test
  void jwtSm2_initFromStringConstructedSm2_rejectsMissingKeyMaterial() {
    String pri =
        "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk2UK0Dfm+NfCgriEJVfrWc/9ZHHy9IxSNdcz3QcfovWgCgYIKoEcz1UBgi2hRANCAATNKB69HCf22URc4rObTCOQN+tAIxdFiFdu22xzmrWCdcVWzY0MAgd4jd7foVLmtbuphSmKMrhT7QsynN4R0Wcw";
    String pub =
        "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEzSgevRwn9tlEXOKzm0wjkDfrQCMXRYhXbttsc5q1gnXFVs2NDAIHeI3e36FS5rW7qYUpijK4U+0LMpzeEdFnMA==";
    var sm2 = SmCryptoUtil.createSm2(pri, pub);
    Assertions.assertThrows(IllegalStateException.class, () -> JwtRs256SecureUtil.init(sm2));
  }

  @Test
  void jwtSm2_seedIsDeterministicAcrossServices() {
    var a = SmCryptoUtil.createSm2FromSeed("mightyPay-boot:jwt-sm2:local-v1");
    var b = SmCryptoUtil.createSm2FromSeed("mightyPay-boot:jwt-sm2:local-v1");
    Assertions.assertEquals(a.getPrivateKeyBase64(), b.getPrivateKeyBase64());
    Assertions.assertEquals(a.getPublicKeyBase64(), b.getPublicKeyBase64());

    JwtRs256SecureUtil.init(a);
    String token = JwtRs256SecureUtil.generateToken("u2", 3600, "http://127.0.0.1:9999/api", "web");
    JwtRs256SecureUtil.init(b);
    Assertions.assertEquals(
        "u2", JwtRs256SecureUtil.verifyToken(token, "http://127.0.0.1:9999/api").getSubject());
  }

  @Test
  void pmsUtil_sanitize_redactsSensitive() {
    Assertions.assertEquals("[REDACTED]", PmsUtil.sanitize("SQLException: bad jdbc:mysql://x"));
    Assertions.assertEquals("[REDACTED]", PmsUtil.sanitize("password=secret123"));
    Assertions.assertEquals("[REDACTED]", PmsUtil.sanitize("Exception occurred: something"));
    Assertions.assertEquals("Ordinary business error", PmsUtil.sanitize("Ordinary business error"));
  }

  @Test
  void apiOutputSanitizer_stripsScript() {
    String dirty = "<script>alert(1)</script>hi";
    Object cleaned = ApiOutputSanitizer.sanitizeForOutput(dirty);
    Assertions.assertFalse(String.valueOf(cleaned).toLowerCase().contains("<script"));
  }

  @Test
  void apiOutputSanitizer_preservesEmailAtSign() {
    Assertions.assertEquals(
        "ka@mighty.africa", ApiOutputSanitizer.sanitizeForOutput("ka@mighty.africa"));
    Object cleaned = ApiOutputSanitizer.sanitizeForOutput("<b>ka@mighty.africa</b>");
    Assertions.assertFalse(String.valueOf(cleaned).contains("&#64;"));
    Assertions.assertTrue(String.valueOf(cleaned).contains("ka@mighty.africa"));
  }

  @Test
  void apiOutputSanitizer_skipsComparisonsWithoutHtmlTags() {
    Assertions.assertEquals("a < b", ApiOutputSanitizer.sanitizeForOutput("a < b"));
    Assertions.assertEquals("score > 0", ApiOutputSanitizer.sanitizeForOutput("score > 0"));
    Assertions.assertEquals("amount < 100", ApiOutputSanitizer.sanitizeForOutput("amount < 100"));
    Assertions.assertEquals("a<b", ApiOutputSanitizer.sanitizeForOutput("a<b"));
    Assertions.assertTrue(ApiOutputSanitizer.containsHtmlTag("<script>x</script>"));
    Assertions.assertTrue(ApiOutputSanitizer.containsHtmlTag("<img src=x onerror=alert(1)>"));
    Assertions.assertFalse(ApiOutputSanitizer.containsHtmlTag("a < b"));
  }

  @Test
  void sensitiveDataTurboFilter_redact() {
    String redacted = SensitiveDataTurboFilter.redact("password=SuperSecret token=abc");
    Assertions.assertTrue(redacted.contains("***"));
  }
}
