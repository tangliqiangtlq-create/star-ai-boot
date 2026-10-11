package org.jeecg.common.exception;

public class FSelfApproveBlockedException extends LedgerSecurityException {
    public static final String ERROR_CODE = "F5-SELF-APPROVE-BLOCKED";
    public FSelfApproveBlockedException(Long proposalId, Long userId) {
        super(ERROR_CODE, "proposalId=" + proposalId + " approver cannot equal proposer userId=" + userId);
    }
}
