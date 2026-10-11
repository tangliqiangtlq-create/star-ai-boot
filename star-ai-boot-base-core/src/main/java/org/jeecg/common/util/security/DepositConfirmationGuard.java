package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;

/**
 * A7-3: Refuse zero-confirmation deposit crediting.
 *
 * <ul>
 *   <li>ETH/Etherscan: require {@code confirmations >= 6}
 *   <li>TRON: require {@code confirmations >= 20}, or block timestamp age ≥ 20×3s when
 *       confirmations are absent. Tronscan {@code /token_trc20/transfers} uses {@code block_ts}.
 * </ul>
 */
public final class DepositConfirmationGuard {

  public static final int ETH_MIN_CONFIRMATIONS = 6;
  public static final int TRON_MIN_CONFIRMATIONS = 20;
  /** Tron ~3s/block → 20 confirmations ≈ 60s. */
  public static final long TRON_MIN_AGE_MS = TRON_MIN_CONFIRMATIONS * 3_000L;

  private DepositConfirmationGuard() {}

  public static boolean hasEnoughConfirmations(JSONObject data, boolean tron) {
    if (data == null) {
      return false;
    }
    Integer conf = data.getInt("confirmations");
    int required = tron ? TRON_MIN_CONFIRMATIONS : ETH_MIN_CONFIRMATIONS;
    if (conf != null) {
      return conf >= required;
    }
    if (tron) {
      Long ts = resolveTronBlockTimestamp(data);
      if (ts != null && ts > 0) {
        long ageMs = System.currentTimeMillis() - ts;
        return ageMs >= TRON_MIN_AGE_MS;
      }
    }
    // Fail-closed when neither confirmations nor usable timestamp is present
    return false;
  }

  public static String rejectReason(JSONObject data, boolean tron) {
    int required = tron ? TRON_MIN_CONFIRMATIONS : ETH_MIN_CONFIRMATIONS;
    Integer conf = data == null ? null : data.getInt("confirmations");
    if (conf != null) {
      return "confirmations=" + conf + " < required=" + required;
    }
    if (tron && data != null) {
      Long ts = resolveTronBlockTimestamp(data);
      if (ts != null) {
        return "block_timestamp age insufficient for " + required + " tron confirmations";
      }
    }
    return StrUtil.format("missing confirmations (required>={})", required);
  }

  /**
   * Tronscan TRC20 transfer payloads commonly expose {@code block_ts}; some gateways use
   * {@code block_timestamp} / {@code blockTimeStamp}.
   */
  public static Long resolveTronBlockTimestamp(JSONObject data) {
    if (data == null) {
      return null;
    }
    Long ts = data.getLong("block_ts");
    if (ts == null) {
      ts = data.getLong("block_timestamp");
    }
    if (ts == null) {
      ts = data.getLong("blockTimeStamp");
    }
    return ts;
  }
}
