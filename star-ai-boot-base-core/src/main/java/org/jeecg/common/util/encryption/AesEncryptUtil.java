package org.jeecg.common.util.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jodd.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.crypto.SmCryptoUtil;

/**
 * Field encryption and decryption tool: new ciphertext uses national cipher text SM4；Decrypting compatibility history AES/CBC/NoPadding。
 */
@Slf4j
public class AesEncryptUtil {

  private static String KEY = EncryptedString.key;

  private static String IV = EncryptedString.iv;

  public static String encrypt(String data, String key, String iv) throws Exception {
    try {
      String keyHex = SmCryptoUtil.deriveSm4KeyHex(key + ":" + iv);
      return SmCryptoUtil.sm4Encrypt(data, keyHex);
    } catch (Exception e) {
      log.error("SM4 encrypt failed");
      return null;
    }
  }

  public static String desEncrypt(String data, String key, String iv) throws Exception {
    if (data != null && data.startsWith(SmCryptoUtil.SM4_PREFIX)) {
      String keyHex = SmCryptoUtil.deriveSm4KeyHex(key + ":" + iv);
      return SmCryptoUtil.sm4Decrypt(data, keyHex);
    }
    byte[] encrypted1 = Base64.decode(data);
    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
    SecretKeySpec keyspec = new SecretKeySpec(key.getBytes(), "AES");
    IvParameterSpec ivspec = new IvParameterSpec(iv.getBytes());
    cipher.init(Cipher.DECRYPT_MODE, keyspec, ivspec);
    byte[] original = cipher.doFinal(encrypted1);
    return new String(original).replaceAll("\\u0000", "");
  }

  public static String encrypt(String data) throws Exception {
    return encrypt(data, KEY, IV);
  }

  public static String desEncrypt(String data) throws Exception {
    return desEncrypt(data, KEY, IV);
  }
}
