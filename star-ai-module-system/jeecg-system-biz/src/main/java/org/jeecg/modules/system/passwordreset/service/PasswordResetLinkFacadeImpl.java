package org.jeecg.modules.system.passwordreset.service;

import lombok.RequiredArgsConstructor;
import org.jeecg.common.system.passwordreset.PasswordResetLinkFacade;
import org.jeecg.common.system.passwordreset.exception.PasswordResetErrorCode;
import org.jeecg.common.system.passwordreset.exception.PasswordResetException;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.modules.system.passwordreset.config.PasswordResetProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Public facade: modules @Autowired PasswordResetLinkFacade and hide Service details.
 */
@Component
@Primary
@RequiredArgsConstructor
public class PasswordResetLinkFacadeImpl implements PasswordResetLinkFacade {

    private final IPasswordResetLinkService passwordResetLinkService;
    private final PasswordResetProperties props;

    private void checkEnabled() {
        if (!props.isEnabled()) {
            throw new PasswordResetException(PasswordResetErrorCode.NOT_PERMITTED, "Password reset link feature is disabled");
        }
    }

    @Override
    public PasswordResetLinkVO createLink(String userId, String username, String source, String creator) {
        checkEnabled();
        return passwordResetLinkService.create(
                userId, username, DEFAULT_TTL_MINUTES, DEFAULT_MAX_FAIL_ATTEMPTS, source, creator, null, null);
    }

    @Override
    public PasswordResetLinkVO createLink(String userId,
                                          String username,
                                          int ttlMinutes,
                                          int maxFailAttempts,
                                          String source,
                                          String creator,
                                          String remark) {
        checkEnabled();
        return passwordResetLinkService.create(
                userId, username, ttlMinutes, maxFailAttempts, source, creator, null, remark);
    }

    @Override
    public PasswordResetLinkVO getLinkByTokenSafe(String token) {
        if (!props.isEnabled()) {
            return null;
        }
        try {
            return passwordResetLinkService.getLinkByTokenSafe(token);
        } catch (Exception e) {
            // Read-only path used while rendering notification email; never let a lookup error
            // break the producer flow or leak a stack trace into the email.
            return null;
        }
    }

    @Override
    public boolean revokeLink(String token, String revokedBy, String revokeReason) {
        checkEnabled();
        return passwordResetLinkService.revoke(token, revokedBy, revokeReason);
    }

    @Override
    public int revokeAllPendingByUsername(String username, String revokedBy, String revokeReason) {
        checkEnabled();
        return passwordResetLinkService.revokeAllPendingByUsername(username, revokedBy, revokeReason);
    }
}
