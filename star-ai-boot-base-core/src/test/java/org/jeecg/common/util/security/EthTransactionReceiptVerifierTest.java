package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class EthTransactionReceiptVerifierTest {

  @Test
  void blankInputsFailClosed() {
    assertFalse(EthTransactionReceiptVerifier.isSuccessfulReceipt(null, null, null));
    assertFalse(EthTransactionReceiptVerifier.isSuccessfulReceipt("", "k", "0xabc"));
    assertFalse(
        EthTransactionReceiptVerifier.isSuccessfulReceipt(
            "https://api.etherscan.io/api", "k", ""));
  }

  @Test
  void privateHostRejected() {
    assertFalse(
        EthTransactionReceiptVerifier.isSuccessfulReceipt(
            "http://127.0.0.1/api", "k", "0xabc"));
  }
}
