package org.jeecg.common.util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.config.crypto.E9AesGcmSivCipher;

@Slf4j
public class PasswordUtil {

  /**
   * @deprecated Legacy PBEWithMD5AndDES algorithm, retained only for read-only compatibility with
   *     pre-V1 (V0/V2) ciphertext. New values are written with {@link E9AesGcmSivCipher} (V1).
   */
  @Deprecated
  public static final String ALGORITHM = "PBEWithMD5AndDES";

  /**
   * @deprecated Legacy static salt, retained only for read-only compatibility with pre-V1 (V0)
   *     ciphertext. Never use for new writes.
   */
  @Deprecated
  public static final String SALT = "63293188";

  private static final int LEGACY_ITERATION_COUNT = 1000;

  private static final int SECURE_ITERATION_COUNT = 100_000;

  private static final String V2_PREFIX = "PBE100K$";

  public static byte[] getSalt() throws Exception {
    SecureRandom random = new SecureRandom();
    return random.generateSeed(8);
  }

  private static byte[] getStaticSalt() {
    return SALT.getBytes(StandardCharsets.UTF_8);
  }

  /** @deprecated Legacy PBE (pre-V1) primitive kept for read-only decryption compatibility only. */
  @Deprecated
  private static Key getPbeKey(char[] passwordChars) {
    SecretKey secretKey = null;
    try {
      SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
      PBEKeySpec keySpec = new PBEKeySpec(passwordChars);
      secretKey = keyFactory.generateSecret(keySpec);
    } catch (Exception e) {
      log.error("PBE key derivation failed");
    }
    return secretKey;
  }

  /** @deprecated Legacy PBE (pre-V1) primitive kept for read-only decryption compatibility only. */
  @Deprecated
  private static Key getPbeKey(String password) {
    if (password == null) {
      return null;
    }
    char[] chars = password.toCharArray();
    try {
      return getPbeKey(chars);
    } finally {
      Arrays.fill(chars, '\0');
    }
  }

  public static String encrypt(String plaintext, String password, String salt) {
    String v1 = E9AesGcmSivCipher.encrypt(plaintext, password + "|" + salt);
    if (v1 != null) {
      return v1;
    }
    Key key = getPbeKey(password);
    if (key == null || plaintext == null || salt == null) {
      return null;
    }
    try {
      byte[] encipheredData =
          doCipher(Cipher.ENCRYPT_MODE, key, salt, plaintext.getBytes(StandardCharsets.UTF_8), SECURE_ITERATION_COUNT);
      return V2_PREFIX + bytesToHexString(encipheredData);
    } catch (Exception e) {
      log.error("PBE encrypt failed");
      return null;
    }
  }

  public static String decrypt(String ciphertext, String password, String salt) {
    if (ciphertext == null || password == null || salt == null) {
      return null;
    }
    if (E9AesGcmSivCipher.isV1(ciphertext)) {
      String v1 = E9AesGcmSivCipher.decrypt(ciphertext, password + "|" + salt);
      if (v1 != null) {
        return v1;
      }
      return null;
    }
    Key key = getPbeKey(password);
    if (key == null) {
      return null;
    }
    try {
      if (ciphertext.startsWith(V2_PREFIX)) {
        String hex = ciphertext.substring(V2_PREFIX.length());
        byte[] passDec =
            doCipher(Cipher.DECRYPT_MODE, key, salt, hexStringToBytes(hex), SECURE_ITERATION_COUNT);
        return new String(passDec, StandardCharsets.UTF_8);
      }
      // Legacy pre-V1 PBE ciphertext (no V1/V2 prefix): read-only compatibility path.
      byte[] passDec =
          doCipher(
              Cipher.DECRYPT_MODE, key, salt, hexStringToBytes(ciphertext), LEGACY_ITERATION_COUNT);
      return new String(passDec, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("PBE decrypt failed");
      return null;
    }
  }

  public static boolean isV0(String ciphertext) {
    if (ciphertext == null) {
      return false;
    }
    return !E9AesGcmSivCipher.isV1(ciphertext);
  }

  public static String rotateIfV0(String plaintextPassword, String oldCiphertext, String salt) {
    if (!isV0(oldCiphertext)) {
      return oldCiphertext;
    }
    if (plaintextPassword == null || salt == null) {
      return oldCiphertext;
    }
    String decrypted = decrypt(oldCiphertext, plaintextPassword, salt);
    if (decrypted == null) {
      return oldCiphertext;
    }
    String re = encrypt(decrypted, plaintextPassword, salt);
    if (re == null) {
      return oldCiphertext;
    }
    log.info("PasswordUtil rotateIfV0: V0 → V1 rotated ({} bytes old)", oldCiphertext.length());
    return re;
  }

  public static <T> T rotateIfV0(T user, String plaintextPassword, UserPasswordAccessor<T> accessor) {
    if (user == null || accessor == null) {
      return user;
    }
    String oldPwd = accessor.getPassword(user);
    String salt = accessor.getSalt(user);
    if (!isV0(oldPwd) || plaintextPassword == null || salt == null) {
      return user;
    }
    String decrypted = decrypt(oldPwd, plaintextPassword, salt);
    if (decrypted == null) {
      return user;
    }
    String newPwd = encrypt(decrypted, plaintextPassword, salt);
    if (newPwd == null) {
      return user;
    }
    accessor.setPassword(user, newPwd);
    log.info("PasswordUtil rotateIfV0: user record V0 → V1 rotated");
    return user;
  }

  public interface UserPasswordAccessor<T> {
    String getPassword(T user);

    String getSalt(T user);

    void setPassword(T user, String newPassword);
  }

  /** @deprecated Legacy PBE (pre-V1) primitive kept for read-only decryption compatibility only. */
  @Deprecated
  private static byte[] doCipher(int mode, Key key, String salt, byte[] input, int iterations)
      throws Exception {
    PBEParameterSpec parameterSpec =
        new PBEParameterSpec(salt.getBytes(StandardCharsets.UTF_8), iterations);
    Cipher cipher = Cipher.getInstance(ALGORITHM);
    cipher.init(mode, key, parameterSpec);
    return cipher.doFinal(input);
  }

  public static String bytesToHexString(byte[] src) {
    StringBuilder stringBuilder = new StringBuilder();
    if (src == null || src.length <= 0) {
      return null;
    }
    for (byte b : src) {
      String hv = Integer.toHexString(b & 0xFF);
      if (hv.length() < 2) {
        stringBuilder.append(0);
      }
      stringBuilder.append(hv);
    }
    return stringBuilder.toString();
  }

  public static byte[] hexStringToBytes(String hexString) {
    if (hexString == null || hexString.isEmpty()) {
      return null;
    }
    hexString = hexString.toUpperCase();
    int length = hexString.length() / 2;
    char[] hexChars = hexString.toCharArray();
    byte[] d = new byte[length];
    for (int i = 0; i < length; i++) {
      int pos = i * 2;
      d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
    }
    return d;
  }

  private static byte charToByte(char c) {
    return (byte) "0123456789ABCDEF".indexOf(c);
  }
}
