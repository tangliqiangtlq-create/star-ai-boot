package org.jeecg.modules.system.passwordreset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.common.system.passwordreset.vo.PasswordResetMetaVO;
import org.jeecg.modules.system.passwordreset.entity.SysPasswordResetLink;

import jakarta.servlet.http.HttpServletRequest;

public interface IPasswordResetLinkService extends IService<SysPasswordResetLink> {

    /** Create reset link (need userId or username; userId wins; usernames must match) */
    PasswordResetLinkVO create(String userId,
                               String username,
                               int ttlMinutes,
                               int maxFailAttempts,
                               String source,
                               String creator,
                               String creatorIp,
                               String remark);

    /**
     * Safe read-only lookup by token: returns link metadata with the backend-built resetUrl, or
     * {@code null} when the token is blank/unknown or the link is no longer PENDING. No access log
     * is written (this is an internal server-side read, e.g. email rendering), and it never throws.
     */
    PasswordResetLinkVO getLinkByTokenSafe(String token);

    /** Validate link + write access log + return meta (with challenge) */
    PasswordResetMetaVO getMeta(String token, HttpServletRequest request);

    /** Internal: validate token without access_log; used before submit; returns link entity */
    SysPasswordResetLink preValidate(String token, HttpServletRequest request);

    /** Submit new password: challenge → RSA → match → strength → != old → BCrypt → USED → logout + clear fails */
    void submitPassword(String token,
                        String challenge,
                        String rsaCacheKey,
                        String encryptedPassword1,
                        String encryptedPassword2,
                        String captcha,
                        String captchaKey,
                        HttpServletRequest request);

    boolean revoke(String token, String revokedBy, String revokeReason);

    int revokeAllPendingByUsername(String username, String revokedBy, String revokeReason);
}
