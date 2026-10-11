package org.jeecg.common.enem;

public enum EmailScene {
  VERIFICATION_CODE("Verification_Code"),
  ACCOUNT_LOCKED("Account_Locked"),
  ACCOUNT_CREATED("Account_Created"),
  SELF_REGISTER_SUCCESS("Self_Register_Success"),
  DEPOSIT_RESULT("Deposit_Result"),
  /** Withdraw cash/withdrawal/transfer result notification on user side (Completed/Failed/Declined) */
  WITHDRAWAL_RESULT("Withdrawal_Result"),
  /** Internal exchange / trade result notification on user side (Completed/Failed) */
  EXCHANGE_RESULT("Exchange_Result"),
  // SendExceptionLog
  EXCEPTION("Exception"),
  // KYC review passed
  KYC_APPROVED("KYC_Approved"),
  // KYC review failed
  KYC_REJECTED("KYC_Rejected"),
  /**
   * @deprecated Merged into {@link #OPS_NOTIFY}; listener still accepts legacy MQ messages
   */
  @Deprecated
  TRADE_REQUEST_CREATED("Trade_Request_Created"),
  /**
   * @deprecated Merged into {@link #OPS_NOTIFY}; listener still accepts legacy MQ messages
   */
  @Deprecated
  PAYOUT_APPROVED("Payout_Approved"),
  /**
   * @deprecated Merged into {@link #OPS_NOTIFY}; listener still accepts legacy MQ messages
   */
  @Deprecated
  PENDING_APPROVAL_ITEM("Pending_Approval_Item"),
  /** General notifications for operations (deposit/Withdraw cash pending review, Trade, withdrawal approval, etc.; Content is driven by params) */
  OPS_NOTIFY("Ops_Notify");

  private String description;

  EmailScene(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
