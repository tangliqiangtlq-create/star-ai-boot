package org.jeecg.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OTPHasher {

  private static final int PBKDF2_ITERATIONS = 120_000;
  private static final int SALT_BYTES = 16;
  private static final int HASH_BYTES = 32;
  private static final String ALGO = "PBKDF2WithHmacSHA256";
  private static final String PREFIX = "OTP1$";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private static final Pattern BAD_VALUE =
      Pattern.compile("(?i)^(CHANGE_ME.*|LOCAL_DEV.*|DEFAULT.*|SAMPLE.*|XXXX.*|PUT[_-]?YOUR[_-]?HERE.*|)$");

  @Value("${OTP_PEPPER:}")
  private String pepper;

  @PostConstruct
  public void validatePepper() {
    if (pepper == null || pepper.length() < 32 || BAD_VALUE.matcher(pepper).matches()) {
      int len = pepper == null ? 0 : pepper.length();
      boolean matches = pepper != null && BAD_VALUE.matcher(pepper).matches();
      throw new BeanInitializationException(
          "D10 OTP_PEPPER env must be >= 32 bytes, injected ONLY via env (NOT nacos yml defaults). "
        + "Current len=" + len + ", matchesBAD_VALUE=" + matches
        + ". See D1/D10 of 00-D remediation plan v1.0 section 2.");
    }
    log.info("D10 OTP_PEPPER OK: len={} (injected via env, not nacos defaults).", pepper.length());
  }

  public String hash(String otpCode, String scene, String account) {
    if (otpCode == null) {
      return null;
    }
    byte[] salt = new byte[SALT_BYTES];
    SECURE_RANDOM.nextBytes(salt);
    byte[] derived = doPbkdf2(otpCode, scene, account, salt, PBKDF2_ITERATIONS, HASH_BYTES);
    if (derived == null) {
      return null;
    }
    String saltB64 = Base64.getEncoder().withoutPadding().encodeToString(salt);
    String hashB64 = Base64.getEncoder().withoutPadding().encodeToString(derived);
    return PREFIX + PBKDF2_ITERATIONS + "$" + saltB64 + "$" + hashB64;
  }

  public boolean verify(String userInput, String storedHash, String scene, String account) {
    if (userInput == null || storedHash == null) {
      return false;
    }
    try {
      if (!storedHash.startsWith(PREFIX)) {
        return false;
      }
      String body = storedHash.substring(PREFIX.length());
      String[] parts = body.split("\\$", -1);
      if (parts.length != 3) {
        return false;
      }
      int iterations = Integer.parseInt(parts[0]);
      byte[] salt = Base64.getDecoder().decode(parts[1]);
      byte[] expected = Base64.getDecoder().decode(parts[2]);
      byte[] actual = doPbkdf2(userInput, scene, account, salt, iterations, expected.length);
      if (actual == null) {
        return false;
      }
      return MessageDigest.isEqual(expected, actual);
    } catch (Exception e) {
      log.warn(
          "OTPHasher.verify exception (storedLen={}): {}",
          storedHash.length(),
          e.getMessage());
      return false;
    }
  }

  private byte[] doPbkdf2(
      String otp, String scene, String account, byte[] salt, int iterations, int outBytes) {
    if (pepper == null) {
      pepper = "";
    }
    String compound =
        pepper
            + "|"
            + (scene == null ? "" : scene)
            + "|"
            + (account == null ? "" : account)
            + "|"
            + otp;
    char[] chars = compound.toCharArray();
    try {
      SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGO);
      PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, outBytes * 8);
      return skf.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException
        | java.security.spec.InvalidKeySpecException e) {
      log.error("OTPHasher PBKDF2 failure: {}", e.getMessage());
      return null;
    } finally {
      java.util.Arrays.fill(chars, '\0');
    }
  }

  @SuppressWarnings("unused")
  private static String bytesToHex(byte[] src) {
    StringBuilder sb = new StringBuilder(src.length * 2);
    for (byte b : src) {
      String hv = Integer.toHexString(b & 0xFF);
      if (hv.length() < 2) {
        sb.append('0');
      }
      sb.append(hv);
    }
    return sb.toString();
  }

  @SuppressWarnings("unused")
  private static byte[] sha256(String raw) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(raw.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
