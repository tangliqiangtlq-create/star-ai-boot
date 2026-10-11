package org.jeecg.config.crypto;

import org.junit.Assert;
import org.junit.Test;

public class E9AesGcmCipherTest {

    @Test
    public void testRoundTripEncryptDecrypt() {
        String plain = "E9-GCM-SIV-ROUNDTRIP-001 user123!@#$%^";
        String pass = "password-" + System.currentTimeMillis();
        String enc = E9AesGcmSivCipher.encrypt(plain, pass);
        Assert.assertNotNull("encrypt must return ciphertext", enc);
        Assert.assertTrue("must start with E9V1:: prefix", enc.startsWith("E9V1::"));
        String dec = E9AesGcmSivCipher.decrypt(enc, pass);
        Assert.assertEquals("roundtrip must match plaintext", plain, dec);
    }

    @Test
    public void testTamperedCiphertextFails() {
        String plain = "tamper test 002";
        String enc = E9AesGcmSivCipher.encrypt(plain, "pw");
        StringBuilder sb = new StringBuilder(enc);
        int p = sb.lastIndexOf(":");
        if (p > 0) sb.setCharAt(p, (char) (sb.charAt(p) + 1));
        String tampered = sb.toString();
        String dec = E9AesGcmSivCipher.decrypt(tampered, "pw");
        Assert.assertNull("tampered ciphertext must return null (fail-closed)", dec);
    }

    @Test
    public void testWrongPasswordFails() {
        String enc = E9AesGcmSivCipher.encrypt("secret A", "pw-A");
        String dec = E9AesGcmSivCipher.decrypt(enc, "pw-B");
        Assert.assertNull("wrong password must fail-closed (return null)", dec);
    }

    @Test
    public void testIsV1PrefixCheck() {
        Assert.assertTrue(E9AesGcmSivCipher.isV1("E9V1::abcdef"));
        Assert.assertFalse(E9AesGcmSivCipher.isV1("PBE100K$deadbeef"));
        Assert.assertFalse(E9AesGcmSivCipher.isV1(null));
        Assert.assertFalse(E9AesGcmSivCipher.isV1(""));
    }

    @Test
    public void testNullEmptyInputFailClosed() {
        Assert.assertNull(E9AesGcmSivCipher.encrypt(null, "pw"));
        Assert.assertNull(E9AesGcmSivCipher.decrypt(null, "pw"));
        Assert.assertNull(E9AesGcmSivCipher.decrypt("E9V1::notvalidbase64==", "pw"));
    }
}
