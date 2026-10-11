package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A7-1 / A7-5: Blockchain deposit scan outbound URL allowlist + SSRF guard.
 *
 * <p>Provider hosts aligned with CodePlan A7-1.1 + deposit-scan dict seed (TronScan list API
 * {@code apilist.tronscanapi.com}). Private/metadata targets blocked via {@link IpAccessGuard}.
 */
public final class BlockchainOutboundGuard {

  /**
   * CodePlan A7-1.1: ETH ({@code *.etherscan.*}, infura, alchemy, quicknode); TRON ({@code
   * *.tronscan.*}, tronscanapi.com, trongrid, getblock).
   */
  private static final Pattern ALLOWED_HOST =
      Pattern.compile(
          "(^|\\.)etherscan\\.[a-z0-9.]+$"
              + "|(^|\\.)infura\\.io$"
              + "|(^|\\.)alchemy\\.com$"
              + "|(^|\\.)quicknode\\.com$"
              + "|(^|\\.)tronscan\\.[a-z0-9.]+$"
              + "|(^|\\.)tronscanapi\\.com$"
              + "|(^|\\.)trongrid\\.io$"
              + "|(^|\\.)getblock\\.io$",
          Pattern.CASE_INSENSITIVE);

  private BlockchainOutboundGuard() {}

  public static void assertAllowedOutboundUrl(String url) {
    if (StrUtil.isBlank(url)) {
      throw new IpAccessGuard.SsrfBlockedException("blockchain outbound URL empty");
    }
    IpAccessGuard.assertNoSsrfTarget(url);
    String host = IpAccessGuard.extractHost(url);
    if (StrUtil.isBlank(host) || !ALLOWED_HOST.matcher(host.toLowerCase(Locale.ROOT)).find()) {
      throw new IpAccessGuard.SsrfBlockedException(
          "blockchain provider host not allowlisted: " + host);
    }
  }

  /**
   * Validate one or more endpoints (comma/semicolon HA list). Returns a normalized
   * comma-joined string of trimmed URLs, or {@code null} when input is blank.
   */
  public static String sanitizeEndpointList(String apiUrlOverride) {
    if (StrUtil.isBlank(apiUrlOverride)) {
      return null;
    }
    List<String> kept = new ArrayList<>();
    for (String part : apiUrlOverride.split("[,;]")) {
      if (StrUtil.isBlank(part)) {
        continue;
      }
      String url = part.trim();
      assertAllowedOutboundUrl(url);
      kept.add(url);
    }
    if (kept.isEmpty()) {
      return null;
    }
    return String.join(",", kept);
  }

  /** First non-blank endpoint from a comma/semicolon HA list (or the raw string). */
  public static String firstEndpoint(String urls) {
    if (StrUtil.isBlank(urls)) {
      return null;
    }
    for (String part : urls.split("[,;]")) {
      if (StrUtil.isNotBlank(part)) {
        return part.trim();
      }
    }
    return null;
  }

  public static boolean isAllowedProviderHost(String host) {
    if (StrUtil.isBlank(host)) {
      return false;
    }
    return ALLOWED_HOST.matcher(host.toLowerCase(Locale.ROOT)).find();
  }
}
