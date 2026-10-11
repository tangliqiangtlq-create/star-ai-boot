package org.jeecg.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GatewayNettyUriCompatConfigTest {

  @Test
  void shouldDecodeEncodedSlashesInQuery() {
    assertEquals(
        "/sys/common/file?objectId=upload/20260709/file.pdf",
        GatewayNettyUriCompatConfig.normalizeRequestUri(
            "/sys/common/file?objectId=upload%2F20260709%2Ffile.pdf"));
  }

  @Test
  void shouldLeavePlainPathUnchanged() {
    assertEquals(
        "/api/sys/common/file?objectId=upload/20260709/file.pdf",
        GatewayNettyUriCompatConfig.normalizeRequestUri(
            "/api/sys/common/file?objectId=upload/20260709/file.pdf"));
  }
}
