package org.jeecg.modules.system.passwordreset.helper;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.Md5Util;
import org.jeecg.common.util.OTPHasher;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.system.util.RandImageUtil;
import org.springframework.stereotype.Component;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;

/**
 * Password-reset captcha helper (P3-1).
 *
 * <p>Policy:
 * <ul>
 *   <li>Captcha length 4; charset 2-9 + A-Z (exclude 0/1/I/O)</li>
 *   <li>Redis Key = PWD_RESET_CAPTCHA:{md5(lowerCode+captchaKey+signSecret)}, value=lowerCode, TTL 5m</li>
 *   <li>Delete after success (one-time); keep on error; fail_count covers abuse</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetCaptchaHelper {

    public static final String REDIS_PREFIX = "PWD_RESET_CAPTCHA:";
    public static final int TTL_MINUTES = 5;
    private static final String CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int LEN = 4;

    private static final SecureRandom SECURE_RANDOM;

    static {
        try {
            SECURE_RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to obtain strong SecureRandom", e);
        }
    }

    private final RedisUtil redisUtil;
    private final JeecgBaseConfig jeecgBaseConfig;
    private final OTPHasher otpHasher;

    private static String secureRandomString(String charset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(SECURE_RANDOM.nextInt(charset.length())));
        }
        return sb.toString();
    }

    /** Generate captcha → return {captchaKey, image(base64)} */
    public Map<String, String> generate() {
        String code = secureRandomString(CHARSET, LEN);
        String lower = code.toLowerCase();
        String captchaKey = IdUtil.fastSimpleUUID();
        String signSecret = jeecgBaseConfig == null ? "" :
                (jeecgBaseConfig.getSignatureSecret() == null ? "" : jeecgBaseConfig.getSignatureSecret());
        String realKey;
        try {
            realKey = Md5Util.md5Encode(lower + captchaKey + signSecret, "utf-8");
        } catch (Exception e) {
            realKey = lower + captchaKey;
        }
        String redisKey = REDIS_PREFIX + realKey;
        String hash = otpHasher.hash(lower, "PWD_RESET_IMAGE", redisKey);
        redisUtil.set(redisKey, hash, TTL_MINUTES * 60);
        try {
            String base64 = RandImageUtil.generate(code);
            return Map.of("captchaKey", captchaKey, "image", base64);
        } catch (Exception e) {
            log.warn("[PasswordResetCaptchaHelper] failed to generate captcha image: {}");
            redisUtil.del(redisKey);
            return Collections.emptyMap();
        }
    }

    /** Validate captcha; delete Redis on success; false on fail or empty captcha/captchaKey */
    public boolean verify(String captcha, String captchaKey) {
        if (captcha == null || captcha.isEmpty() || captchaKey == null || captchaKey.isEmpty()) {
            return false;
        }
        // CSP-EXEMPT: T2 双杀强化——per-captchaKey 频控（5 次错作废图形验证码）
        String countKey = "pwd_reset_captcha_fail:" + captchaKey;
        try {
            Long cnt = redisUtil.incr(countKey, 1L);
            if (cnt != null && cnt == 1L) {
                redisUtil.expire(countKey, 660L);
            }
            if (cnt != null && cnt >= 5) {
                String lowerTmp = captcha.toLowerCase();
                String signSecretTmp = jeecgBaseConfig == null ? "" :
                    (jeecgBaseConfig.getSignatureSecret() == null ? "" : jeecgBaseConfig.getSignatureSecret());
                String realKeyTmp;
                try {
                    realKeyTmp = Md5Util.md5Encode(lowerTmp + captchaKey + signSecretTmp, "utf-8");
                } catch (Exception eTmp) {
                    realKeyTmp = lowerTmp + captchaKey;
                }
                redisUtil.del(REDIS_PREFIX + realKeyTmp);
                redisUtil.del(countKey);
                return false;
            }
        } catch (Exception ignoreT2) {}
        String lower = captcha.toLowerCase();
        String signSecret = jeecgBaseConfig == null ? "" :
                (jeecgBaseConfig.getSignatureSecret() == null ? "" : jeecgBaseConfig.getSignatureSecret());
        String realKey;
        try {
            realKey = Md5Util.md5Encode(lower + captchaKey + signSecret, "utf-8");
        } catch (Exception e) {
            realKey = lower + captchaKey;
        }
        String redisKey = REDIS_PREFIX + realKey;
        try {
            Object v = redisUtil.get(redisKey);
            if (v == null) return false;
            boolean ok;
            String sv = v.toString();
            if (lower.equalsIgnoreCase(sv)) {
                ok = true; // Transitional plain-equals compatibility: legacy captcha codes expire naturally within 11 minutes
            } else {
                ok = otpHasher.verify(lower, sv, "PWD_RESET_IMAGE", redisKey);
            }
            if (ok) {
                redisUtil.del(redisKey);
            }
            return ok;
        } catch (Exception e) {
            log.warn("[PasswordResetCaptchaHelper] verify Exception：{}");
            return false;
        }
    }
}
