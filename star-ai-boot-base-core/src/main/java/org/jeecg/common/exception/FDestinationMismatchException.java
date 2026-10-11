package org.jeecg.common.exception;

public class FDestinationMismatchException extends LedgerSecurityException {
    public FDestinationMismatchException(String message) {
        super("F10A", message);
    }
}
