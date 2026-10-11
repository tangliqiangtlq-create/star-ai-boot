package org.jeecg.modules.system.passwordreset;

/**
 * Password-reset constants (align with link/access_log/usage_log values).
 * Service references link-status constants via implements.
 */
public interface PasswordResetConstants {

    /** Link status (sys_password_reset_link.status) */
    String PENDING = "PENDING";
    String USED    = "USED";
    String REVOKED = "REVOKED";
    String LOCKED  = "LOCKED";
    String EXPIRED = "EXPIRED";

    /** Access-result constants */
    interface AccessResult {
        String OK             = "OK";
        String INVALID        = "INVALID";
        String EXPIRED        = "EXPIRED";
        String LOCKED         = "LOCKED";
        String SIGN_ERR       = "SIGN_ERR";
        String CHALLENGE_FAIL = "CHALLENGE_FAIL";
    }

    /** Usage-result constants */
    interface UsageResult {
        String SUCCESS = "SUCCESS";
        String FAIL    = "FAIL";
    }

    /** Failure stage (fine-grained) */
    interface FailStage {
        String SIGN_ERR            = "SIGN_ERR";
        String INVALID_LINK        = "INVALID_LINK";
        String CHALLENGE_FAIL      = "CHALLENGE_FAIL";
        String CAPTCHA_FAIL        = "CAPTCHA_FAIL";
        String PWD_MISMATCH        = "PWD_MISMATCH";
        String PWD_WEAK            = "PWD_WEAK";
        String PWD_SAME_AS_OLD     = "PWD_SAME_AS_OLD";
        String RSA_DECRYPT         = "RSA_DECRYPT";
        String BCYPT_FAIL          = "BCYPT_FAIL";
        String CONCURRENT_CONFLICT = "CONCURRENT_CONFLICT";
        String NOT_PERMITTED       = "NOT_PERMITTED";
        String USER_NOT_FOUND      = "USER_NOT_FOUND";
        String USER_DISABLED       = "USER_DISABLED";
    }
}
