package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

class DepositConfirmationGuardTest {

  @Test
  void ethRequiresSixConfirmations() {
    JSONObject data = new JSONObject().set("confirmations", 5);
    assertFalse(DepositConfirmationGuard.hasEnoughConfirmations(data, false));
    data.set("confirmations", 6);
    assertTrue(DepositConfirmationGuard.hasEnoughConfirmations(data, false));
  }

  @Test
  void tronAcceptsAgedBlockTimestamp() {
    JSONObject data =
        new JSONObject().set("block_timestamp", System.currentTimeMillis() - 61_000L);
    assertTrue(DepositConfirmationGuard.hasEnoughConfirmations(data, true));
    data.set("block_timestamp", System.currentTimeMillis() - 1_000L);
    assertFalse(DepositConfirmationGuard.hasEnoughConfirmations(data, true));
  }

  @Test
  void tronAcceptsAgedBlockTsFromTronscan() {
    // Tronscan /api/new/token_trc20/transfers uses block_ts (not block_timestamp)
    JSONObject data = new JSONObject().set("block_ts", System.currentTimeMillis() - 61_000L);
    assertTrue(DepositConfirmationGuard.hasEnoughConfirmations(data, true));
    data.set("block_ts", System.currentTimeMillis() - 1_000L);
    assertFalse(DepositConfirmationGuard.hasEnoughConfirmations(data, true));
  }

  @Test
  void missingConfirmationsFailClosedForEth() {
    assertFalse(DepositConfirmationGuard.hasEnoughConfirmations(new JSONObject(), false));
  }
}
