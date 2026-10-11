package org.jeecg.common.exception;

public class LedgerSecurityException extends RuntimeException {
    private final String errorCode;

    public LedgerSecurityException(String errorCode, String message) {
        super("[" + errorCode + "] " + message);
        this.errorCode = errorCode;
    }

    public LedgerSecurityException(String errorCode, String message, Throwable cause) {
        super("[" + errorCode + "] " + message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
