package org.jeecg.common.exception;

public class FBizSemRequiredException extends LedgerSecurityException {
    public FBizSemRequiredException() {
        super("F12-C11", "C11 limits are business semantics; requires finance + product dual sign-off, gray release, and historical reconciliation recalc in a separate PR");
    }

    public FBizSemRequiredException(String detail) {
        super("F12-C11", "C11 limits are business semantics; requires finance + product dual sign-off, gray release, and historical reconciliation recalc in a separate PR: " + detail);
    }
}
