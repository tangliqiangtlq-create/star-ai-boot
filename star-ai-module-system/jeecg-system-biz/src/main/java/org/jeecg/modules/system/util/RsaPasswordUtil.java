package org.jeecg.modules.system.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Transport password decrypt: prefer SM2; compatible with legacy RSA private-key cache.
 */
@Slf4j
@Component
public class RsaPasswordUtil {

  @Autowired(required = false)
  private RedisUtil redisUtil;

  public String decryptPassword(String cacheKey, String encryptedPassword) {
    if (redisUtil == null) {
      log.warn("RedisUtil not injected; cannot decrypt password");
      return null;
    }
    if (StrUtil.isBlank(cacheKey) || StrUtil.isBlank(encryptedPassword)) {
      log.warn("cacheKey or encrypted password is empty");
      return null;
    }
    try {
      Object privateKeyObj = redisUtil.get(cacheKey);
      if (privateKeyObj == null) {
        log.warn("Transport private key expired or missing, cacheKey: {}", cacheKey);
        return null;
      }
      String privateKeyBase64 = privateKeyObj.toString();
      // SM2 cache: sm2:privateKey:* or values prefixed with SM2$
      if (cacheKey.startsWith("sm2:") || privateKeyBase64.startsWith("SM2$")) {
        String pk =
            privateKeyBase64.startsWith("SM2$")
                ? privateKeyBase64.substring(4)
                : privateKeyBase64;
        return SmCryptoUtil.sm2Decrypt(encryptedPassword, pk);
      }
      // Compatible with legacy RSA
      RSA rsa = new RSA(privateKeyBase64, null);
      byte[] decryptedPasswordBytes = rsa.decrypt(encryptedPassword, KeyType.PrivateKey);
      if (decryptedPasswordBytes == null) {
        log.error("RSA decryption returned null, cacheKey: {}");
        return null;
      }
      return new String(decryptedPasswordBytes, java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Transport passphrase decryption failed, cacheKey: {}", cacheKey, e);
      return null;
    }
  }

  public String decryptPasswordAndClean(String cacheKey, String encryptedPassword) {
    String password = decryptPassword(cacheKey, encryptedPassword);
    if (password != null) {
      evictPrivateKey(cacheKey);
    }
    return password;
  }

  public void evictPrivateKey(String cacheKey) {
    if (redisUtil == null || StrUtil.isBlank(cacheKey)) {
      return;
    }
    try {
      redisUtil.del(cacheKey);
    } catch (Exception e) {
      log.warn("Failed to delete transport private key, cacheKey: {}");
    }
  }

  public boolean isEncrypted(String password) {
    if (StrUtil.isBlank(password)) {
      return false;
    }
    return password.length() > 50 && password.matches("^[A-Za-z0-9+/=]+$");
  }
}
