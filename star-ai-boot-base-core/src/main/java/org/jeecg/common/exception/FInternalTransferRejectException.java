package org.jeecg.common.exception;

public class FInternalTransferRejectException extends LedgerSecurityException {
    public FInternalTransferRejectException(String reason) {
        super("F11", reason);
    }
}
