package com.xxl.job.admin.core.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.SecureRandom;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * Cookie.Util
 *
 * @author xuxueli 2015-12-12 18:01:06
 */
public class CookieUtil {

	// Default cache time, unit/second, 2H
	private static final int COOKIE_MAX_AGE = Integer.MAX_VALUE;
	// Save path, root path
	private static final String COOKIE_PATH = "/";
	private static final String COOKIE_VALUE_SEPARATOR = ".";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static volatile String RUNTIME_COOKIE_SIGN_SECRET;
	
	/**
	 * Save
	 *
	 * @param response
	 * @param key
	 * @param value
	 * @param ifRemember 
	 */
	public static void set(HttpServletResponse response, String key, String value, boolean ifRemember) {
		int age = ifRemember?COOKIE_MAX_AGE:-1;
		set(response, key, signValue(value), null, COOKIE_PATH, age, true);
	}

	/**
	 * Save
	 *
	 * @param response
	 * @param key
	 * @param value
	 * @param maxAge
	 */
	private static void set(HttpServletResponse response, String key, String value, String domain, String path, int maxAge, boolean isHttpOnly) {
		ResponseCookie cookie;
		if (domain != null) {
			if (maxAge >= 0) {
				cookie =
						ResponseCookie.from(key, value)
								.path(path)
								.domain(domain)
								.maxAge(maxAge)
								.httpOnly(isHttpOnly)
								.secure(true)
								.sameSite("Lax")
								.build();
			} else {
				cookie =
						ResponseCookie.from(key, value)
								.path(path)
								.domain(domain)
								.httpOnly(isHttpOnly)
								.secure(true)
								.sameSite("Lax")
								.build();
			}
		} else {
			if (maxAge >= 0) {
				cookie =
						ResponseCookie.from(key, value)
								.path(path)
								.maxAge(maxAge)
								.httpOnly(isHttpOnly)
								.secure(true)
								.sameSite("Lax")
								.build();
			} else {
				cookie =
						ResponseCookie.from(key, value)
								.path(path)
								.httpOnly(isHttpOnly)
								.secure(true)
								.sameSite("Lax")
								.build();
			}
		}
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
	
	/**
	 * Queryvalue
	 *
	 * @param request
	 * @param key
	 * @return
	 */
	public static String getValue(HttpServletRequest request, String key) {
		Cookie cookie = get(request, key);
		if (cookie != null) {
			return verifyAndExtract(cookie.getValue());
		}
		return null;
	}

	private static String signValue(String rawValue) {
		String base64Payload = Base64.getUrlEncoder().withoutPadding()
			.encodeToString(rawValue.getBytes(StandardCharsets.UTF_8));
		String signature = hmacHex(base64Payload);
		return base64Payload + COOKIE_VALUE_SEPARATOR + signature;
	}

	private static String verifyAndExtract(String signedValue) {
		if (signedValue == null) {
			return null;
		}
		int split = signedValue.lastIndexOf(COOKIE_VALUE_SEPARATOR);
		if (split <= 0 || split >= signedValue.length() - 1) {
			return null;
		}
		String payload = signedValue.substring(0, split);
		String signature = signedValue.substring(split + 1);
		String expect = hmacHex(payload);
		if (!constantTimeEquals(expect, signature)) {
			return null;
		}
		try {
			byte[] decoded = Base64.getUrlDecoder().decode(payload);
			return new String(decoded, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String hmacHex(String payload) {
		String secret = getCookieSignSecret();
		if (secret == null || secret.trim().isEmpty()) {
			return "";
		}
		try {
			byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
			byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
			HMac mac = new HMac(new SM3Digest());
			mac.init(new KeyParameter(secretBytes));
			mac.update(payloadBytes, 0, payloadBytes.length);
			byte[] sign = new byte[mac.getMacSize()];
			mac.doFinal(sign, 0);
			StringBuilder sb = new StringBuilder(sign.length * 2);
			for (byte b : sign) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null || a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}

	private static String getCookieSignSecret() {
		String fromProp = System.getProperty("xxl.cookie.sign.secret");
		if (fromProp != null && !fromProp.trim().isEmpty()) {
			return fromProp;
		}
		String fromEnv = System.getenv("XXL_COOKIE_SIGN_SECRET");
		if (fromEnv != null && !fromEnv.trim().isEmpty()) {
			return fromEnv;
		}
		if (RUNTIME_COOKIE_SIGN_SECRET == null) {
			synchronized (CookieUtil.class) {
				if (RUNTIME_COOKIE_SIGN_SECRET == null) {
					byte[] seed = new byte[32];
					SECURE_RANDOM.nextBytes(seed);
					RUNTIME_COOKIE_SIGN_SECRET = Base64.getUrlEncoder().withoutPadding().encodeToString(seed);
				}
			}
		}
		return RUNTIME_COOKIE_SIGN_SECRET;
	}

	/**
	 * QueryCookie
	 *
	 * @param request
	 * @param key
	 */
	private static Cookie get(HttpServletRequest request, String key) {
		Cookie[] arr_cookie = request.getCookies();
		if (arr_cookie != null && arr_cookie.length > 0) {
			for (Cookie cookie : arr_cookie) {
				if (cookie.getName().equals(key)) {
					return cookie;
				}
			}
		}
		return null;
	}
	
	/**
	 * DeleteCookie
	 *
	 * @param request
	 * @param response
	 * @param key
	 */
	public static void remove(HttpServletRequest request, HttpServletResponse response, String key) {
		Cookie cookie = get(request, key);
		if (cookie != null) {
			set(response, key, "", null, COOKIE_PATH, 0, true);
		}
	}

}
