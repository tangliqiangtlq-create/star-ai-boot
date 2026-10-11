package org.jeecg.common.env.secrets;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Local envelope encryption (AES-256-GCM) for Compose / offline / pre-KMS bootstrap.
 *
 * <p>Wire format: {@code AESGCM:<base64(iv12 || ciphertext || tag16)>}
 */
public final class LocalAesGcmSecretsCrypto {

  public static final String PREFIX = "AESGCM:";
  private static final int IV_LEN = 12;
  private static final int TAG_BITS = 128;

  private LocalAesGcmSecretsCrypto() {}

  public static boolean isEncrypted(String value) {
    return value != null && value.startsWith(PREFIX);
  }

  public static String encryptToPrefixed(byte[] masterKey32, String plaintext) {
    try {
      byte[] iv = new byte[IV_LEN];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(masterKey32), new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
      buf.put(iv);
      buf.put(ct);
      return PREFIX + Base64.getEncoder().encodeToString(buf.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM encrypt failed: " + e.getMessage(), e);
    }
  }

  public static String decryptPrefixed(byte[] masterKey32, String prefixed) {
    if (!isEncrypted(prefixed)) {
      return prefixed;
    }
    try {
      byte[] raw = Base64.getDecoder().decode(prefixed.substring(PREFIX.length()).trim());
      if (raw.length <= IV_LEN + 16) {
        throw new IllegalArgumentException("AESGCM payload too short");
      }
      byte[] iv = Arrays.copyOfRange(raw, 0, IV_LEN);
      byte[] ct = Arrays.copyOfRange(raw, IV_LEN, raw.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(masterKey32), new GCMParameterSpec(TAG_BITS, iv));
      byte[] pt = cipher.doFinal(ct);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("AES-GCM decrypt failed: " + e.getMessage(), e);
    }
  }

  private static SecretKey key(byte[] masterKey32) {
    if (masterKey32 == null || masterKey32.length != 32) {
      throw new IllegalArgumentException("master key must be exactly 32 bytes (AES-256)");
    }
    return new SecretKeySpec(masterKey32, "AES");
  }
}
