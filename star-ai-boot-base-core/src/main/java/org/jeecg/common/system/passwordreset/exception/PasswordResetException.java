package org.jeecg.common.system.passwordreset.exception;

import lombok.Getter;

@Getter
public class PasswordResetException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Error code, see Password reset exception code table */
    private final String code;

    public PasswordResetException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PasswordResetException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
