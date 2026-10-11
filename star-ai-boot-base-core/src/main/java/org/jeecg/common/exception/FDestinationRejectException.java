package org.jeecg.common.exception;

public class FDestinationRejectException extends LedgerSecurityException {
    public FDestinationRejectException(String message) {
        super("F10B", message);
    }
}
