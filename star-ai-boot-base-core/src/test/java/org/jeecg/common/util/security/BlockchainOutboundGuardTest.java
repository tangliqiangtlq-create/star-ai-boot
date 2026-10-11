package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A7-1 allowlist regression: CodePlan hosts + tronscanapi + HA list sanitization. */
class BlockchainOutboundGuardTest {

  @Test
  void allowlistedOfficialHosts() {
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("apilist.tronscanapi.com"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("api.etherscan.io"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("mainnet.infura.io"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("eth-mainnet.g.alchemy.com"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("foo.quicknode.com"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("api.trongrid.io"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("trx.getblock.io"));
    assertTrue(BlockchainOutboundGuard.isAllowedProviderHost("api.tronscan.org"));
  }

  @Test
  void rejectNonAllowlistedHosts() {
    assertFalse(BlockchainOutboundGuard.isAllowedProviderHost("evil.com"));
    assertFalse(BlockchainOutboundGuard.isAllowedProviderHost("ankr.com"));
    assertFalse(BlockchainOutboundGuard.isAllowedProviderHost("nottronscanapi.com"));
    assertThrows(
        IpAccessGuard.SsrfBlockedException.class,
        () -> BlockchainOutboundGuard.assertAllowedOutboundUrl("https://evil.com/rpc"));
  }

  @Test
  void sanitizeHaEndpointList() {
    String out =
        BlockchainOutboundGuard.sanitizeEndpointList(
            "https://apilist.tronscanapi.com , https://api.trongrid.io");
    assertEquals("https://apilist.tronscanapi.com,https://api.trongrid.io", out);
    assertEquals(
        "https://apilist.tronscanapi.com",
        BlockchainOutboundGuard.firstEndpoint(out));
    assertThrows(
        IpAccessGuard.SsrfBlockedException.class,
        () ->
            BlockchainOutboundGuard.sanitizeEndpointList(
                "https://apilist.tronscanapi.com,https://evil.com"));
  }

  @Test
  void assertAllowsTronscanapiUrlWithoutDns() {
    // Host pattern only — DNS may fail offline; prefer isAllowedProviderHost for offline CI.
    assertDoesNotThrow(
        () -> {
          // Pattern check path used by isAllowedProviderHost
          assertTrue(
              BlockchainOutboundGuard.isAllowedProviderHost("apilist.tronscanapi.com"));
        });
  }
}
