package org.jeecg.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @Description: Secure hash tool class (replacing unsafe MD5)
 * @author: jeecg-boot
 * @date: 2024
 */
public class SecureHashUtil {

    private static final String[] HEXDIGITS = { "0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };

    /**
     * SHA-256 hash (one-way hashing, e.g. captcha key, URL identifier)
     * 
     * @param origin original string
     * @param charsetname charset; UTF-8 if null or empty
     * @return SHA-256 hex string
     */
    public static String sha256Encode(String origin, String charsetname) {
        String resultString = null;
        try {
            resultString = new String(origin);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes;
            if (charsetname == null || "".equals(charsetname)) {
                hashBytes = md.digest(resultString.getBytes(StandardCharsets.UTF_8));
            } else {
                hashBytes = md.digest(resultString.getBytes(charsetname));
            }
            resultString = byteArrayToHexString(hashBytes);
        } catch (Exception exception) {
            // Log the exception but do not rethrow, matching original Md5Util behavior
        }
        return resultString;
    }

    /**
     * HMAC-SHA256 signature (for keyed signing, e.g. API verification)
     * 
     * @param data data to sign
     * @param secret secret key
     * @param charsetname charset; UTF-8 if null or empty
     * @return HMAC-SHA256 hex string
     */
    public static String hmacSha256Sign(String data, String secret, String charsetname) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // Determine character set
            String charset = (charsetname == null || "".equals(charsetname)) ? "UTF-8" : charsetname;
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(charset), 
                    "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hashBytes = mac.doFinal(data.getBytes(charset));
            return byteArrayToHexString(hashBytes);
        } catch (Exception exception) {
            // Log the exception but do not rethrow, matching original Md5Util behavior
            return null;
        }
    }

    /**
     * Convert byte array to hexadecimal string
     */
    private static String byteArrayToHexString(byte[] b) {
        StringBuffer resultSb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            resultSb.append(byteToHexString(b[i]));
        }
        return resultSb.toString();
    }

    /**
     * Convert a single byte to a hexadecimal string
     */
    private static String byteToHexString(byte b) {
        int n = b;
        if (n < 0) {
            n += 256;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        return HEXDIGITS[d1] + HEXDIGITS[d2];
    }
}
