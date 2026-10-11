package org.jeecg.modules.system.mfa;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.jeecg.common.util.encryption.AesEncryptUtil;
import org.junit.Assert;
import org.junit.Test;

public class TotpCryptoTest {

  @Test
  public void shouldAuthorizeTotpWithEncryptedSecret() throws Exception {
    GoogleAuthenticator authenticator = new GoogleAuthenticator();
    GoogleAuthenticatorKey key = authenticator.createCredentials();
    String secret = key.getKey();

    String encrypted = AesEncryptUtil.encrypt(secret);
    String decrypted = AesEncryptUtil.desEncrypt(encrypted);

    Assert.assertEquals(secret, decrypted);

    int otp = authenticator.getTotpPassword(secret);
    Assert.assertTrue(authenticator.authorize(decrypted, otp));
  }
}

