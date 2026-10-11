package org.jeecg.common.system.passwordreset;

import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;

/**
 * Reset the Password external link public facade (can be directly injected by any module).
 * Shield low-level details such as token generation, signature, storage, and permissions.
 */
public interface PasswordResetLinkFacade {

    int DEFAULT_TTL_MINUTES = 30;
    int MIN_TTL_MINUTES = 5;
    int MAX_TTL_MINUTES = 1440;
    int DEFAULT_MAX_FAIL_ATTEMPTS = 5;
    int MIN_MAX_FAIL_ATTEMPTS = 1;
    int MAX_MAX_FAIL_ATTEMPTS = 20;

    /**
     * Create a reset link with default parameters (TTL 30min, lock after 5 failures).
     *
     * @param userId   target user id (sys_user.id; preferred for precise lookup)
     * @param username target account (at least one of userId/username required; username may be null when userId is provided; if both are provided, userId takes precedence and username must match)
     * @param source   source scenario (password_reset_source dict code)
     * @param creator  creator userId or "SYS"
     * @return link metadata (token + resetUrl + expireTime)
     */
    PasswordResetLinkVO createLink(String userId, String username, String source, String creator);

    /**
     * Create reset link with custom parameters.
     *
     * @param userId           target user id (sys_user.id; prefer passing; username alone also works)
     * @param username         target account (at least one with userId; if both passed, userId takes precedence and username is validated)
     * @param ttlMinutes       validity in minutes
     * @param maxFailAttempts  failure lock threshold
     * @param source           source scenario
     * @param creator          creator
     * @param remark           ops remark (e.g. ticket ID)
     * @return link metadata
     */
    PasswordResetLinkVO createLink(String userId,
                                   String username,
                                   int ttlMinutes,
                                   int maxFailAttempts,
                                   String source,
                                   String creator,
                                   String remark);

    /**
     * Safe read-only lookup by token for server-side rendering/notification (e.g. the ACCOUNT_CREATED
     * email consumer). Never throws; returns {@code null} when the feature is disabled, the token is
     * blank/unknown, or the link is no longer PENDING (used/revoked/locked/expired).
     *
     * <p>The returned {@link PasswordResetLinkVO} carries the backend-built {@code resetUrl}
     * (portalOrigin + pathPrefix + token); callers must never trust a URL from the MQ payload.
     *
     * @param token link token (the {@code pwResetLinkId} carried on MQ)
     * @return link metadata with resetUrl, or {@code null} when unavailable
     */
    PasswordResetLinkVO getLinkByTokenSafe(String token);

    /**
     * Revoke a PENDING link by token.
     *
     * @return true if revoked successfully; false if link not found or not PENDING
     */
    boolean revokeLink(String token, String revokedBy, String revokeReason);

    /**
     * Batch revoke all PENDING links by account.
     *
     * @return number of links actually revoked
     */
    int revokeAllPendingByUsername(String username, String revokedBy, String revokeReason);
}
