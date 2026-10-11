package org.jeecg.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @Description: Encrypt tool (deprecated, please use SecureHashUtil)
 * @author: jeecg-boot
 * @deprecated The MD5 algorithm is not secure and has been replaced by SHA-256. Please use {@link SecureHashUtil#sha256Encode(String, String)} instead
 */
@Deprecated
public class Md5Util {

    private static final String[] HEXDIGITS = { "0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };

	public static String byteArrayToHexString(byte[] b) {
		StringBuffer resultSb = new StringBuffer();
		for (int i = 0; i < b.length; i++){
			resultSb.append(byteToHexString(b[i]));
		}
		return resultSb.toString();
	}

	private static String byteToHexString(byte b) {
		int n = b;
		if (n < 0) {
			n += 256;
		}
		int d1 = n / 16;
		int d2 = n % 16;
		return HEXDIGITS[d1] + HEXDIGITS[d2];
	}

	/**
	 * MD5 encoding method (deprecated; internally uses SHA-256 for compatibility)
	 * 
	 * @param origin original string
	 * @param charsetname character set
	 * @return hexadecimal hash string (actually computed with SHA-256)
	 * @deprecated use {@link SecureHashUtil#sha256Encode(String, String)} instead
	 */
	@Deprecated
	public static String md5Encode(String origin, String charsetname) {
		// To maintain backward compatibility, the method signature remains unchanged, but the internal implementation is changed to the more secure SHA-256
		// This avoids the need to modify a large amount of existing code and improves security.
		return SecureHashUtil.sha256Encode(origin, charsetname);
	}

}
