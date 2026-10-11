package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * A7-3: Independent {@code eth_getTransactionReceipt} RPC check before ETH deposit credit.
 *
 * <p>Fail-closed: network/parse errors and non-{@code 0x1} status reject crediting.
 */
@Slf4j
public final class EthTransactionReceiptVerifier {

  private static final int TIMEOUT_MS = 15_000;

  private EthTransactionReceiptVerifier() {}

  /**
   * @param apiBaseUrl Etherscan-compatible base URL (may already contain query params)
   * @param apiKey optional apikey query param (authMode=0); blank when Bearer/header auth is used
   * @param txHash transaction hash
   * @return true only when receipt exists and {@code status == 0x1}
   */
  public static boolean isSuccessfulReceipt(String apiBaseUrl, String apiKey, String txHash) {
    if (StrUtil.isBlank(apiBaseUrl) || StrUtil.isBlank(txHash)) {
      return false;
    }
    try {
      String base = apiBaseUrl.trim();
      while (base.endsWith("/")) {
        base = base.substring(0, base.length() - 1);
      }
      StringBuilder u = new StringBuilder(base);
      u.append(base.contains("?") ? "&" : "?");
      u.append("module=proxy&action=eth_getTransactionReceipt&txhash=").append(txHash.trim());
      if (StrUtil.isNotBlank(apiKey)) {
        u.append("&apikey=").append(apiKey.trim());
      }
      String url = u.toString();
      BlockchainOutboundGuard.assertAllowedOutboundUrl(url);
      String body = HttpRequest.get(url).timeout(TIMEOUT_MS).execute().body();
      if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
        log.warn("A7-3 eth_getTransactionReceipt empty/non-json for hash={}", txHash);
        return false;
      }
      JSONObject root = JSONUtil.parseObj(body);
      Object result = root.get("result");
      if (result == null || result instanceof String) {
        // etherscan returns result:null or error string on miss
        log.warn(
            "A7-3 eth_getTransactionReceipt missing receipt hash={}, result={}", txHash, result);
        return false;
      }
      JSONObject receipt = root.getJSONObject("result");
      if (receipt == null) {
        return false;
      }
      String status = receipt.getStr("status");
      // post-Byzantium: 0x1 success; pre-Byzantium may omit status — fail-closed without status
      boolean ok = "0x1".equalsIgnoreCase(status) || "1".equals(status);
      if (!ok) {
        log.warn("A7-3 eth_getTransactionReceipt status={} hash={}", status, txHash);
      }
      return ok;
    } catch (IpAccessGuard.SsrfBlockedException ex) {
      log.warn("A7-3 eth_getTransactionReceipt SSRF blocked: {}", ex.getMessage());
      return false;
    } catch (Exception ex) {
      log.warn("A7-3 eth_getTransactionReceipt failed hash={}: {}", txHash, ex.toString());
      return false;
    }
  }
}
