package org.jeecg.config.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class E9AesGcmSivCipher {

  public static final String V1_PREFIX = "E9V1::";

  public static final int GCM_NONCE_LENGTH = 16;
  public static final int GCM_TAG_BITS = 96;
  public static final int PBKDF2_SALT_LENGTH = 16;
  public static final int PBKDF2_ITERATIONS_DEFAULT = 600_000;
  public static final int AES_KEY_LENGTH_BITS = 256;
  public static final String PBKDF2_ALG = "PBKDF2WithHmacSHA256";
  public static final String CIPHER_ALG = "AES/GCM/NoPadding";
  public static final String KEY_ALG = "AES";

  private static final SecureRandom RNG = new SecureRandom();

  private E9AesGcmSivCipher() {}

  public static int getIterations() {
    String raw = System.getProperty("crypto.pbkdf2.iterations");
    if (raw != null && !raw.isBlank()) {
      try {
        int v = Integer.parseInt(raw.trim());
        if (v >= 10_000) {
          return v;
        }
      } catch (NumberFormatException ignored) {
      }
    }
    String env = System.getenv("CRYPTO_PBKDF2_ITERATIONS");
    if (env != null && !env.isBlank()) {
      try {
        int v = Integer.parseInt(env.trim());
        if (v >= 10_000) {
          return v;
        }
      } catch (NumberFormatException ignored) {
      }
    }
    return PBKDF2_ITERATIONS_DEFAULT;
  }

  public static String encrypt(String plaintext, String password) {
    if (plaintext == null || password == null) {
      return null;
    }
    try {
      int iterations = getIterations();
      byte[] salt = new byte[PBKDF2_SALT_LENGTH];
      RNG.nextBytes(salt);
      byte[] nonce = new byte[GCM_NONCE_LENGTH];
      RNG.nextBytes(nonce);

      SecretKey key = deriveKey(password.toCharArray(), salt, iterations);
      Cipher cipher = Cipher.getInstance(CIPHER_ALG);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
      cipher.init(Cipher.ENCRYPT_MODE, key, spec);
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      ByteBuffer buf =
          ByteBuffer.allocate(
              4 + PBKDF2_SALT_LENGTH + 4 + GCM_NONCE_LENGTH + ciphertext.length);
      buf.putInt(iterations);
      buf.put(salt);
      buf.putInt(nonce.length);
      buf.put(nonce);
      buf.put(ciphertext);

      String body = Base64.getEncoder().withoutPadding().encodeToString(buf.array());
      return V1_PREFIX + body;
    } catch (Exception e) {
      log.error("E9 AES-256-GCM encrypt failed", e);
      return null;
    }
  }

  public static String decrypt(String ciphertext, String password) {
    if (ciphertext == null || password == null) {
      return null;
    }
    if (!ciphertext.startsWith(V1_PREFIX)) {
      return null;
    }
    try {
      String body = ciphertext.substring(V1_PREFIX.length());
      byte[] raw = Base64.getDecoder().decode(body);
      ByteBuffer buf = ByteBuffer.wrap(raw);

      int iterations = buf.getInt();
      byte[] salt = new byte[PBKDF2_SALT_LENGTH];
      buf.get(salt);
      int nonceLen = buf.getInt();
      byte[] nonce = new byte[nonceLen];
      buf.get(nonce);
      byte[] ct = new byte[buf.remaining()];
      buf.get(ct);

      SecretKey key = deriveKey(password.toCharArray(), salt, iterations);
      Cipher cipher = Cipher.getInstance(CIPHER_ALG);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, nonce);
      cipher.init(Cipher.DECRYPT_MODE, key, spec);
      byte[] pt = cipher.doFinal(ct);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("E9 AES-256-GCM decrypt failed");
      return null;
    }
  }

  public static boolean isV1(String ciphertext) {
    return ciphertext != null && ciphertext.startsWith(V1_PREFIX);
  }

  private static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
      throws Exception {
    char[] copy = Arrays.copyOf(password, password.length);
    try {
      KeySpec spec =
          new PBEKeySpec(copy, salt, iterations, AES_KEY_LENGTH_BITS);
      SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALG);
      byte[] material = skf.generateSecret(spec).getEncoded();
      return new SecretKeySpec(material, KEY_ALG);
    } finally {
      Arrays.fill(copy, '\0');
    }
  }
}
