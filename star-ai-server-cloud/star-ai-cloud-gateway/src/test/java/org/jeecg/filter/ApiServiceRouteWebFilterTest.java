package org.jeecg.filter;

import org.junit.jupiter.api.Test;

class ApiServiceRouteWebFilterTest {

  @Test
  void locatorMode_shouldRewriteApiSysPathToDiscoveryLocator() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/jeecg-system/sys/randomImage/key",
        ApiServiceRouteWebFilter.rewritePath("/api/sys/randomImage/key", true));
  }

  @Test
  void locatorMode_shouldRewriteBareSysPath() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/jeecg-system/sys/common/file",
        ApiServiceRouteWebFilter.rewritePath("/sys/common/file", true));
  }

  @Test
  void explicitRoutesMode_shouldOnlyStripApiKeepSys() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/sys/randomImage/key",
        ApiServiceRouteWebFilter.rewritePath("/api/sys/randomImage/key", false));
  }

  @Test
  void explicitRoutesMode_shouldKeepBareSysUnchanged() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/sys/randomImage/key",
        ApiServiceRouteWebFilter.rewritePath("/sys/randomImage/key", false));
  }

  @Test
  void shouldKeepAlreadyDiscoveryPrefixedPath() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/jeecg-system/sys/randomImage/key",
        ApiServiceRouteWebFilter.rewritePath("/jeecg-system/sys/randomImage/key", true));
    org.junit.jupiter.api.Assertions.assertEquals(
        "/jeecg-system/sys/randomImage/key",
        ApiServiceRouteWebFilter.rewritePath("/jeecg-system/sys/randomImage/key", false));
  }

  @Test
  void locatorMode_shouldRewriteApiDcsPath() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/jeecg-dcs/dcs/currency/list",
        ApiServiceRouteWebFilter.rewritePath("/api/dcs/currency/list", true));
  }

  @Test
  void explicitRoutesMode_shouldStripApiForDcs() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/dcs/currency/list",
        ApiServiceRouteWebFilter.rewritePath("/api/dcs/currency/list", false));
  }

  @Test
  void shouldStripApiPrefixForWebsocketPath() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/websocket/2090298429381480449",
        ApiServiceRouteWebFilter.rewritePath("/api/websocket/2090298429381480449", true));
    org.junit.jupiter.api.Assertions.assertEquals(
        "/websocket/2090298429381480449",
        ApiServiceRouteWebFilter.rewritePath("/api/websocket/2090298429381480449", false));
  }

  @Test
  void shouldStripApiPrefixForNewsWebsocketPath() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/newsWebsocket/channel",
        ApiServiceRouteWebFilter.rewritePath("/api/newsWebsocket/channel", false));
  }

  @Test
  void shouldKeepBareWebsocketPath() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/websocket/2090298429381480449",
        ApiServiceRouteWebFilter.rewritePath("/websocket/2090298429381480449", false));
  }

  @Test
  void legacyRewriteToDiscoveryPath_defaultsToLocatorMode() {
    org.junit.jupiter.api.Assertions.assertEquals(
        "/jeecg-system/sys/randomImage/key",
        ApiServiceRouteWebFilter.rewriteToDiscoveryPath("/api/sys/randomImage/key"));
  }
}
