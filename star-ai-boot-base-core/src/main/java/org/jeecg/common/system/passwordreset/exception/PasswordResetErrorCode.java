package org.jeecg.common.system.passwordreset.exception;

/** Password reset exception code constant (external error code) */
public interface PasswordResetErrorCode {

    String LINK_INVALID             = "PWD_RESET_LINK_INVALID";
    String PWD_MISMATCH             = "PWD_RESET_PWD_MISMATCH";
    String PWD_WEAK                 = "PWD_RESET_PWD_WEAK";
    String PWD_SAME_AS_OLD          = "PWD_RESET_PWD_SAME_AS_OLD";
    String RSA_DECRYPT_FAIL         = "PWD_RESET_RSA_DECRYPT_FAIL";
    String CAPTCHA_REQUIRED         = "PWD_RESET_CAPTCHA_REQUIRED";
    String SUBMIT_TOO_FAST          = "PWD_RESET_SUBMIT_TOO_FAST";
    String USER_DISABLED            = "PWD_RESET_USER_DISABLED";
    String TOO_MANY_PENDING         = "PWD_RESET_TOO_MANY_PENDING";
    String NOT_FOUND                = "PWD_RESET_NOT_FOUND";
    String NOT_PERMITTED            = "PWD_RESET_NOT_PERMITTED";
    String CHALLENGE_FAIL           = "PWD_RESET_CHALLENGE_FAIL";
}
