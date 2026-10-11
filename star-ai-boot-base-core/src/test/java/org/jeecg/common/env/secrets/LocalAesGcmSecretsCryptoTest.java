package org.jeecg.common.env.secrets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class LocalAesGcmSecretsCryptoTest {

  @Test
  public void roundTrip() {
    byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    assertEquals(32, key.length);
    String enc = LocalAesGcmSecretsCrypto.encryptToPrefixed(key, "super-secret-db-password");
    assertTrue(enc.startsWith(LocalAesGcmSecretsCrypto.PREFIX));
    assertEquals(
        "super-secret-db-password", LocalAesGcmSecretsCrypto.decryptPrefixed(key, enc));
  }
}
