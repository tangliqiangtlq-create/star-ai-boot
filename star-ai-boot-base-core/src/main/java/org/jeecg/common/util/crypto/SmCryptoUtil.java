package org.jeecg.common.util.crypto;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;
import cn.hutool.crypto.digest.BCrypt;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * National secret tools: SM2 / SM3 / SM4, and SM3 password hashing.
 *
 * <p>New writes are all state secrets; check/decryption is compatible with historical BCrypt, AES, and RSA ciphertexts.
 */
public final class SmCryptoUtil {

  private static final String BC = "BC";
  private static final Object LOCK = new Object();
  private static volatile boolean providerRegistered;

  public static final String PWD_PREFIX = "SM3PWD$";
  public static final String SM4_PREFIX = "SM4$";
  private static final int PWD_ITERATIONS = 100_000;

  private SmCryptoUtil() {}

  private static void ensureProvider() {
    if (providerRegistered) {
      return;
    }
    synchronized (LOCK) {
      if (!providerRegistered) {
        if (Security.getProvider(BC) == null) {
          Security.addProvider(new BouncyCastleProvider());
        }
        providerRegistered = true;
      }
    }
  }

  // ---------- SM3 ----------

  public static String sm3Hex(String input) {
    if (input == null) {
      return null;
    }
    ensureProvider();
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SM3", BC);
      return HexUtil.encodeHexStr(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SM3 digest failed", e);
    }
  }

  public static String sm3Hex(byte[] input) {
    ensureProvider();
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SM3", BC);
      return HexUtil.encodeHexStr(md.digest(input));
    } catch (Exception e) {
      throw new IllegalStateException("SM3 digest failed", e);
    }
  }

  /** Password hash: {@code SM3PWD$iterations$saltHex$hashHex}. */
  public static String hashPassword(String plainPassword) {
    if (StrUtil.isBlank(plainPassword)) {
      throw new IllegalArgumentException("password required");
    }
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    String hash = sm3PasswordDigest(plainPassword, salt, PWD_ITERATIONS);
    return PWD_PREFIX + PWD_ITERATIONS + "$" + HexUtil.encodeHexStr(salt) + "$" + hash;
  }

  /**
   * check password: supports SM3PWD, BCrypt ($2a$/$2b$).
   *
   * <p>When either the plain text or library hash is empty/blank, {@code false} will be returned, and null/empty passwords are prohibited from participating in authentication.
   */
  public static boolean checkPassword(String plainPassword, String stored) {
    if (StrUtil.isBlank(plainPassword) || StrUtil.isBlank(stored)) {
      return false;
    }
    if (stored.startsWith(PWD_PREFIX)) {
      String body = stored.substring(PWD_PREFIX.length());
      String[] parts = body.split("\\$", 3);
      if (parts.length != 3) {
        return false;
      }
      int iterations = Integer.parseInt(parts[0]);
      byte[] salt = HexUtil.decodeHex(parts[1]);
      String expect = parts[2];
      return expect.equalsIgnoreCase(sm3PasswordDigest(plainPassword, salt, iterations));
    }
    if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
      return BCrypt.checkpw(plainPassword, stored);
    }
    return false;
  }

  /**
   * Unified credential verification: modern SM3PWD/BCrypt hash first, with a fallback to the
   * legacy V0 PBE format ({@code PasswordUtil.decrypt(storedHash, plain, salt) == username}).
   *
   * <p>Single source of truth shared by interactive login and the internal S2S
   * {@code /sys/api/verifyPassword} endpoint (P3 — dcs must verify passwords without receiving
   * the stored hash over Feign). Returns {@code false} on any blank input or non-modern hash
   * that fails the legacy recovery; modern hashes are NEVER routed through the PBE path.
   */
  public static boolean verifyPasswordWithLegacyFallback(
      String username, String plainPassword, String salt, String storedHash) {
    if (StrUtil.hasBlank(username, plainPassword, storedHash)) {
      return false;
    }
    if (checkPassword(plainPassword, storedHash)) {
      return true;
    }
    // Legacy V0 PBE only applies to non-modern formats
    if (StrUtil.isBlank(salt)
        || storedHash.startsWith(PWD_PREFIX)
        || storedHash.startsWith("$2a$")
        || storedHash.startsWith("$2b$")
        || storedHash.startsWith("$2y$")) {
      return false;
    }
    try {
      return username.equals(
          org.jeecg.common.util.PasswordUtil.decrypt(storedHash, plainPassword, salt));
    } catch (Exception e) {
      return false;
    }
  }

  private static String sm3PasswordDigest(String plain, byte[] salt, int iterations) {
    ensureProvider();
    try {
      java.security.MessageDigest md = java.security.MessageDigest.getInstance("SM3", BC);
      md.update(salt);
      md.update(plain.getBytes(StandardCharsets.UTF_8));
      byte[] dig = md.digest();
      for (int i = 1; i < iterations; i++) {
        md.reset();
        dig = md.digest(dig);
      }
      return HexUtil.encodeHexStr(dig);
    } catch (Exception e) {
      throw new IllegalStateException("SM3 password digest failed", e);
    }
  }

  // ---------- SM4 ----------

  public static String generateSm4KeyHex() {
    ensureProvider();
    try {
      KeyGenerator kg = KeyGenerator.getInstance("SM4", BC);
      kg.init(128, new SecureRandom());
      SecretKey key = kg.generateKey();
      return HexUtil.encodeHexStr(key.getEncoded());
    } catch (Exception e) {
      throw new IllegalStateException("SM4 keygen failed", e);
    }
  }

  /** Derive a 16-byte SM4 key (hex) from arbitrary password material. */
  public static String deriveSm4KeyHex(String material) {
    String hex = sm3Hex(material == null ? "" : material);
    return hex.substring(0, 32);
  }

  /** SM4/CBC/PKCS7，Return {@code SM4$ivHex:cipherHex}。 */
  public static String sm4Encrypt(String plaintext, String keyHex) {
    ensureProvider();
    try {
      byte[] key = normalizeSm4Key(keyHex);
      byte[] iv = new byte[16];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", BC);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return SM4_PREFIX + HexUtil.encodeHexStr(iv) + ":" + HexUtil.encodeHexStr(encrypted);
    } catch (Exception e) {
      throw new IllegalStateException("SM4 encrypt failed", e);
    }
  }

  /** Decrypted {@code SM4$ivHex:cipherHex} or naked {@code ivHex:cipherHex}. */
  public static String sm4Decrypt(String payload, String keyHex) {
    ensureProvider();
    try {
      String body = payload.startsWith(SM4_PREFIX) ? payload.substring(SM4_PREFIX.length()) : payload;
      String[] parts = body.split(":", 2);
      if (parts.length != 2) {
        throw new IllegalArgumentException("invalid SM4 payload");
      }
      byte[] key = normalizeSm4Key(keyHex);
      byte[] iv = HexUtil.decodeHex(parts[0]);
      byte[] cipherBytes = HexUtil.decodeHex(parts[1]);
      Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", BC);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
      return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("SM4 decrypt failed", e);
    }
  }

  /** SM4 with fixed IV (compatible with old AES key/iv negotiated lengths for responding to Encrypt). */
  public static String sm4EncryptWithIv(String plaintext, byte[] key16, byte[] iv16) {
    ensureProvider();
    try {
      Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", BC);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key16, "SM4"), new IvParameterSpec(iv16));
      return Base64.getEncoder()
          .encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SM4 encrypt failed", e);
    }
  }

  private static byte[] normalizeSm4Key(String keyHexOrRaw) {
    if (keyHexOrRaw == null) {
      throw new IllegalArgumentException("sm4 key required");
    }
    String t = keyHexOrRaw.trim();
    if (t.length() == 32 && t.matches("(?i)[0-9a-f]+")) {
      return HexUtil.decodeHex(t);
    }
    // Non-hex: Derive 16 bytes with SM3
    return HexUtil.decodeHex(deriveSm4KeyHex(t));
  }

  // ---------- SM2 ----------

  public static SM2 createSm2() {
    ensureProvider();
    return SmUtil.sm2();
  }

  public static SM2 createSm2(String privateKeyBase64, String publicKeyBase64) {
    ensureProvider();
    return new SM2(privateKeyBase64, publicKeyBase64);
  }

  /**
   * Deterministic SM2 keypair from a shared seed (same seed ⇒ same keys on every service/JVM).
   *
   * <p>Used so system can sign login JWT and dcs/processengine can verify without Redis races.
   */
  public static SM2 createSm2FromSeed(String seed) {
    ensureProvider();
    if (StrUtil.isBlank(seed)) {
      throw new IllegalArgumentException("sm2 seed required");
    }
    try {
      byte[] digest = SmUtil.sm3().digest(seed.getBytes(StandardCharsets.UTF_8));
      SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
      random.setSeed(digest);
      java.security.KeyPairGenerator kpg =
          java.security.KeyPairGenerator.getInstance("EC", BC);
      kpg.initialize(new java.security.spec.ECGenParameterSpec("sm2p256v1"), random);
      java.security.KeyPair kp = kpg.generateKeyPair();
      return new SM2(kp.getPrivate(), kp.getPublic());
    } catch (Exception e) {
      throw new IllegalStateException("create SM2 from seed failed", e);
    }
  }

  public static String sm2EncryptBase64(String plaintext, String publicKeyBase64) {
    SM2 sm2 = createSm2(null, publicKeyBase64);
    return sm2.encryptBase64(plaintext, KeyType.PublicKey);
  }

  /**
   * Decrypt transmission ciphertext: compatible with sm-crypto hex (front-end) and hutool Base64.
   *
   * <p>sm-crypto {@code doEncrypt} returns C1C3C2 hex without the uncompressed-point prefix {@code
   * 04}; BouncyCastle requires that prefix on C1. Try both forms.
   */
  public static String sm2Decrypt(String cipher, String privateKeyBase64) {
    SM2 sm2 = createSm2(privateKeyBase64, null);
    try {
      if (cipher != null && cipher.matches("(?i)^[0-9a-f]+$") && cipher.length() % 2 == 0) {
        return decryptSm2Hex(sm2, cipher);
      }
      return sm2.decryptStr(cipher, KeyType.PrivateKey);
    } catch (Exception e) {
      throw new IllegalStateException("SM2 decrypt failed", e);
    }
  }

  private static String decryptSm2Hex(SM2 sm2, String hexCipher) {
    Exception last = null;
    for (String candidate : sm2HexCipherCandidates(hexCipher)) {
      try {
        byte[] plain = sm2.decrypt(HexUtil.decodeHex(candidate), KeyType.PrivateKey);
        return new String(plain, StandardCharsets.UTF_8);
      } catch (Exception e) {
        last = e;
      }
    }
    throw new IllegalStateException("SM2 decrypt failed", last);
  }

  /** Prefer raw hex; if it lacks C1 {@code 04}, also try with the prefix (sm-crypto). */
  private static String[] sm2HexCipherCandidates(String hexCipher) {
    if (hexCipher.regionMatches(true, 0, "04", 0, 2)) {
      return new String[] {hexCipher, hexCipher.substring(2)};
    }
    return new String[] {"04" + hexCipher, hexCipher};
  }

  /** Returns the uncompressed public key hex available to sm-crypto (usually starting with 04). */
  public static String sm2PublicKeyHex(SM2 sm2) {
    ensureProvider();
    try {
      byte[] encoded = cn.hutool.crypto.BCUtil.encodeECPublicKey(sm2.getPublicKey());
      return HexUtil.encodeHexStr(encoded);
    } catch (Exception e) {
      throw new IllegalStateException("encode SM2 public key failed", e);
    }
  }

  public static byte[] sm2Sign(byte[] data, String privateKeyBase64) {
    SM2 sm2 = createSm2(privateKeyBase64, null);
    return sm2.sign(data);
  }

  public static boolean sm2Verify(byte[] data, byte[] sign, String publicKeyBase64) {
    SM2 sm2 = createSm2(null, publicKeyBase64);
    return sm2.verify(data, sign);
  }

  public static void wipe(byte[] data) {
    if (data != null) {
      Arrays.fill(data, (byte) 0);
    }
  }
}
