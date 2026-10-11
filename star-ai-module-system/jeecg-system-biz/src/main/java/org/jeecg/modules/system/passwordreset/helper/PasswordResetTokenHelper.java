package org.jeecg.modules.system.passwordreset.helper;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.system.passwordreset.config.PasswordResetProperties;
import org.jeecg.common.system.passwordreset.exception.PasswordResetErrorCode;
import org.jeecg.common.system.passwordreset.exception.PasswordResetException;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.zip.CRC32;

/**
 * Token / signature / challenge utilities.
 *
 * 1) Token: 30-char URL-safe = version(2) + rand(22) + crc_base64url(6)
 * 2) HMAC-SHA256 anti-forgery: dual-secret verify (primary fail → try secretOld; 7-day transition)
 * 3) Challenge: JWT-like header.payload.signature bound to token + clientIp; TTL; one-time
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetTokenHelper {

    private static final String TOKEN_VERSION = "01";
    private static final String URL_SAFE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final String CHALLENGE_REDIS_PREFIX = "pwd_reset:challenge_used:";
    private static final String RESET_NONCE_PREFIX = "reset_nonce:";
    private static final char DOT = '.';

    private static final SecureRandom SECURE_RANDOM;

    static {
        try {
            SECURE_RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to obtain strong SecureRandom instance", e);
        }
    }

    private final PasswordResetProperties props;
    private final RedisUtil redisUtil;

    private volatile HMac hmacPrimary;
    private volatile HMac hmacOld;

    @PostConstruct
    public void initKeys() {
        if (props.getSecret() == null || props.getSecret().isEmpty()) {
            throw new JeecgBootException("[jeecg.password-reset.secret] must be configured or a fallback must be available");
        }
        byte[] primary = props.getSecret().getBytes(StandardCharsets.UTF_8);
        hmacPrimary = SecureUtil.hmac(HmacAlgorithm.HmacSHA256, primary);
        if (props.getSecretOld() != null && !props.getSecretOld().isEmpty()) {
            hmacOld = SecureUtil.hmac(HmacAlgorithm.HmacSHA256, props.getSecretOld().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String secureRandomString(String charset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(SECURE_RANDOM.nextInt(charset.length())));
        }
        return sb.toString();
    }

    /* ========== Token generation ========== */

    /** Generate 30-char URL-safe token (+ version + CRC) using SecureRandom.getInstanceStrong() */
    public String generateToken() {
        String version = TOKEN_VERSION;
        String rand = secureRandomString(URL_SAFE_CHARS, 22);
        String raw = version + rand;
        CRC32 crc32 = new CRC32();
        crc32.update(raw.getBytes(StandardCharsets.UTF_8));
        long crc = crc32.getValue();
        byte[] crcBytes = new byte[4];
        crcBytes[0] = (byte) ((crc >>> 24) & 0xFF);
        crcBytes[1] = (byte) ((crc >>> 16) & 0xFF);
        crcBytes[2] = (byte) ((crc >>> 8) & 0xFF);
        crcBytes[3] = (byte) (crc & 0xFF);
        String crcPart = Base64.encodeUrlSafe(crcBytes).replace("=", "").substring(0, 6);
        return raw + crcPart;
    }

    /** HMAC-SHA256(token) as 64-hex; store in sys_password_reset_link.signature */
    public String computeSignature(String token) {
        return HexUtil.encodeHexStr(hmacPrimary.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Validate HMAC signature. Return true to continue DB lookup; false returns SIGN_ERR directly.
     * Dual secret: primary secret match -> OK; if primary fails and secretOld exists -> retry once; fail only if both mismatch.
     */
    public VerifyResult verifySignature(String token, String dbSignature) {
        if (token == null || dbSignature == null) return VerifyResult.MISMATCH;
        String s1 = computeSignature(token);
        if (s1.equalsIgnoreCase(dbSignature)) return VerifyResult.OK_PRIMARY;
        if (hmacOld != null) {
            String s2 = HexUtil.encodeHexStr(hmacOld.digest(token.getBytes(StandardCharsets.UTF_8)));
            if (s2.equalsIgnoreCase(dbSignature)) {
                log.warn("Password reset token signed with old secret, recommend clearing secretOld soon");
                return VerifyResult.OK_OLD;
            }
        }
        return VerifyResult.MISMATCH;
    }

    /* ========== Challenge (prevent URL leakage + forwarding) ========== */

    /**
     * Generate challenge = base64url(header).base64url(payload).base64url(signature)
     * payload = {token, ip, ts, ttl}, signature binding.
     */
    public String generateChallenge(String token, String clientIp) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long expireAt = nowSec + props.getChallengeTtlSeconds();
        String jti = java.util.UUID.randomUUID().toString().replace("-", "");
        String header = "{\"alg\":\"HS256\",\"typ\":\"PWD_RESET_CHALLENGE_V1\"}";
        String payload = String.format("{\"tok\":\"%s\",\"ip\":\"%s\",\"iat\":%d,\"exp\":%d,\"rnd\":%d,\"jti\":\"%s\"}",
                escapeJson(token),
                escapeJson(clientIp == null ? "" : clientIp),
                nowSec,
                expireAt,
                SECURE_RANDOM.nextInt(1, Integer.MAX_VALUE),
                jti);
        String h = Base64.encodeUrlSafe(header.getBytes(StandardCharsets.UTF_8)).replace("=", "");
        String p = Base64.encodeUrlSafe(payload.getBytes(StandardCharsets.UTF_8)).replace("=", "");
        String signingInput = h + DOT + p;
        String sig = HexUtil.encodeHexStr(hmacPrimary.digest(signingInput.getBytes(StandardCharsets.UTF_8)));
        try {
            redisUtil.set(RESET_NONCE_PREFIX + jti, "1", props.getChallengeTtlSeconds() * 2L);
        } catch (Exception ignore) {
        }
        return h + DOT + p + DOT + sig;
    }

    /**
     * Validate challenge only (signature / token / IP / expiry); do not write to Redis.
     * Call {@link #consumeChallenge(String, String)} after password set succeeds for consumption (replay protection),
     * to avoid false "already used" errors when password validation fails or the frontend double-submits.
     */
    public void verifyChallenge(
            String challenge, String token, String clientIp, HttpServletRequest request) {
        parseAndVerifyChallenge(challenge, token, clientIp, request);
    }

    /**
     * Mark challenge as used (called after successful password set). Duplicate consumption throws CHALLENGE_FAIL.
     */
    public void consumeChallenge(String challenge, String token) {
        ParsedChallenge parsed = parseAndVerifyChallenge(challenge, token, null, null);
        boolean consumed = false;
        if (parsed.jti != null && !parsed.jti.isEmpty()) {
            try {
                String nonceKey = RESET_NONCE_PREFIX + parsed.jti;
                if (Boolean.TRUE.equals(redisUtil.hasKey(nonceKey))) {
                    redisUtil.del(nonceKey);
                    consumed = !Boolean.TRUE.equals(redisUtil.hasKey(nonceKey));
                }
            } catch (Exception ignore) {
            }
        }
        if (!consumed) {
            String redisKey = CHALLENGE_REDIS_PREFIX + parsed.tok + "_" + parsed.sig;
            if (Boolean.TRUE.equals(redisUtil.hasKey(redisKey))) {
                throw new PasswordResetException(
                        PasswordResetErrorCode.CHALLENGE_FAIL,
                        "Challenge already used. Please reopen the link");
            }
            redisUtil.set(redisKey, "1", props.getChallengeTtlSeconds() * 2L);
        }
    }

    /**
     * @deprecated use {@link #verifyChallenge} + {@link #consumeChallenge}
     */
    @Deprecated
    public void verifyAndConsumeChallenge(
            String challenge, String token, String clientIp, HttpServletRequest request) {
        verifyChallenge(challenge, token, clientIp, request);
        consumeChallenge(challenge, token);
    }

    private ParsedChallenge parseAndVerifyChallenge(
            String challenge, String token, String clientIp, HttpServletRequest request) {
        if (StrUtil.isBlank(challenge)) {
            throw new PasswordResetException(PasswordResetErrorCode.CHALLENGE_FAIL, "Challenge is empty. Please reopen the link");
        }
        String[] parts = challenge.split("\\.");
        if (parts.length != 3) {
            throw new PasswordResetException(PasswordResetErrorCode.CHALLENGE_FAIL, "Invalid challenge format");
        }
        String h = parts[0], p = parts[1], s = parts[2];
        String signingInput = h + DOT + p;
        String expected = HexUtil.encodeHexStr(hmacPrimary.digest(signingInput.getBytes(StandardCharsets.UTF_8)));
        boolean sigOk = expected.equalsIgnoreCase(s);
        if (!sigOk && hmacOld != null) {
            String expectedOld = HexUtil.encodeHexStr(hmacOld.digest(signingInput.getBytes(StandardCharsets.UTF_8)));
            sigOk = expectedOld.equalsIgnoreCase(s);
        }
        if (!sigOk) {
            throw new PasswordResetException(PasswordResetErrorCode.CHALLENGE_FAIL, "Challenge signature mismatch");
        }
        String payload;
        try {
            payload = new String(Base64.decode(p), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PasswordResetException(PasswordResetErrorCode.CHALLENGE_FAIL, "Failed to decode challenge");
        }
        String tok = jsonStringField(payload, "tok");
        String ip = jsonStringField(payload, "ip");
        String jti = jsonStringField(payload, "jti");
        long exp = jsonLongField(payload, "exp");
        if (tok == null || !tok.equals(token)) {
            throw new PasswordResetException(PasswordResetErrorCode.CHALLENGE_FAIL, "Challenge does not match token");
        }
        // Validate IP when clientIp/request non-null (consume may skip)
        if (request != null || clientIp != null) {
            if (!ipMatchesChallenge(ip, clientIp, request)) {
                log.warn(
                        "password-reset challenge IP mismatch challengeIp={} resolvedIp={} xff={} remote={}",
                        ip,
                        clientIp,
                        request == null ? null : request.getHeader("X-Forwarded-For"),
                        request == null ? null : request.getRemoteAddr());
                throw new PasswordResetException(
                        PasswordResetErrorCode.CHALLENGE_FAIL,
                        "Challenge does not match the request origin. Please refresh the page and try again");
            }
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        if (exp <= nowSec) {
            throw new PasswordResetException(PasswordResetErrorCode.CHALLENGE_FAIL, "Challenge expired. Please refresh the link and try again");
        }
        return new ParsedChallenge(tok, s, jti);
    }

    private static final class ParsedChallenge {
        private final String tok;
        private final String sig;
        private final String jti;

        private ParsedChallenge(String tok, String sig, String jti) {
            this.tok = tok;
            this.sig = sig;
            this.jti = jti;
        }
    }

    /** Pass if challenge.ip matches any candidate IP from the request */
    private static boolean ipMatchesChallenge(String challengeIp, String resolvedIp, HttpServletRequest request) {
        if (challengeIp == null) {
            challengeIp = "";
        }
        if (challengeIp.equals(resolvedIp == null ? "" : resolvedIp)) {
            return true;
        }
        if (request == null) {
            return false;
        }
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (StrUtil.isNotBlank(resolvedIp)) {
            candidates.add(resolvedIp.trim());
        }
        String remote = request.getRemoteAddr();
        if (StrUtil.isNotBlank(remote)) {
            if ("0:0:0:0:0:0:0:1".equals(remote)) {
                remote = "127.0.0.1";
            }
            candidates.add(remote.trim());
        }
        String xri = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(xri)) {
            candidates.add(xri.trim());
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xff)) {
            for (String part : xff.split(",")) {
                if (StrUtil.isNotBlank(part)) {
                    candidates.add(part.trim());
                }
            }
        }
        return candidates.contains(challengeIp);
    }

    /* ========== Utilities ========== */

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String jsonStringField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int i = json.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }

    private static long jsonLongField(String json, String field) {
        String key = "\"" + field + "\":";
        int i = json.indexOf(key);
        if (i < 0) return -1L;
        int start = i + key.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end == start) return -1L;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public enum VerifyResult {
        OK_PRIMARY, OK_OLD, MISMATCH
    }
}
