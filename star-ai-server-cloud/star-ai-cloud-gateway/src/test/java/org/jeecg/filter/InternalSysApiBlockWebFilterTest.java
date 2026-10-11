package org.jeecg.filter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the gateway path-normalization write-back.
 *
 * <p>Incident: {@code POST /api/sys/dict/getDictItems/dcs_currency,code,code,%20type='1'} answered
 * HTTP 500 (pre-auth) instead of reaching the system service. {@link
 * InternalSysApiBlockWebFilter#normalizePath(String)} percent-decodes the path (twice) for the deny
 * check and then wrote the <em>decoded</em> path back into the exchange with {@code build(true)}
 * (="already encoded"); the decoded space made Spring's URI validation throw {@code
 * IllegalArgumentException: Invalid character ' ' for PATH}, which escaped the WebFilter.
 */
class InternalSysApiBlockWebFilterTest {

  private static final String HOST = "https://mpay-admin.xnow.xyz";

  private static String gatewayPath(String rawPath) {
    // mirrors InternalSysApiBlockWebFilter#filter: normalize, then strip the leading /api for the
    // deny check
    String normalized = InternalSysApiBlockWebFilter.normalizePath(rawPath);
    String stripped = normalized;
    if (stripped.startsWith("/api/")) {
      stripped = stripped.substring(4);
      if (!stripped.startsWith("/")) {
        stripped = "/" + stripped;
      }
    }
    return stripped;
  }

  // ---------- the reported incident ----------

  @Test
  void decodedSpaceInPathIsReEncodedInsteadOfThrowing() {
    String raw = "/sys/dict/getDictItems/dcs_currency,code,code,%20type='1'";
    String normalized = InternalSysApiBlockWebFilter.normalizePath(raw);
    assertEquals("/sys/dict/getDictItems/dcs_currency,code,code, type='1'", normalized);

    URI rebuilt =
        assertDoesNotThrow(
            () -> InternalSysApiBlockWebFilter.rebuildUri(URI.create(HOST + raw), normalized));
    assertEquals(raw, rebuilt.getRawPath());
    assertEquals(HOST, rebuilt.getScheme() + "://" + rebuilt.getHost());
  }

  @Test
  void reportedIncidentPathIsNotBlocked() {
    assertFalse(
        InternalSysApiBlockWebFilter.isBlocked(
            gatewayPath("/api/sys/dict/getDictItems/dcs_currency,code,code,%20type='1'")));
  }

  @Test
  void doubleEncodedSpaceIsAlsoReEncoded() {
    String raw = "/api/sys/dict/getDictItems/dcs_currency,code,code,%2520";
    String normalized = InternalSysApiBlockWebFilter.normalizePath(raw);
    URI rebuilt =
        assertDoesNotThrow(
            () -> InternalSysApiBlockWebFilter.rebuildUri(URI.create(HOST + raw), normalized));
    assertEquals("/api/sys/dict/getDictItems/dcs_currency,code,code,%20", rebuilt.getRawPath());
  }

  @Test
  void decodedPipeAndTabAreReEncoded() {
    for (String[] c : new String[][] {{"%7C1", "%7C1"}, {"%09", "%09"}}) {
      String raw = "/api/sys/dict/getDictItems/dcs_currency,code,code," + c[0];
      String normalized = InternalSysApiBlockWebFilter.normalizePath(raw);
      URI rebuilt =
          assertDoesNotThrow(
              () -> InternalSysApiBlockWebFilter.rebuildUri(URI.create(HOST + raw), normalized));
      assertEquals(
          "/api/sys/dict/getDictItems/dcs_currency,code,code," + c[1], rebuilt.getRawPath());
    }
  }

  @Test
  void legalDecodedCharactersArePreservedVerbatim() {
    String raw = "/api/sys/dict/getDictItems/dcs_currency,code,code,%3D1";
    String normalized = InternalSysApiBlockWebFilter.normalizePath(raw);
    assertEquals("/api/sys/dict/getDictItems/dcs_currency,code,code,=1", normalized);
    URI rebuilt =
        assertDoesNotThrow(
            () -> InternalSysApiBlockWebFilter.rebuildUri(URI.create(HOST + raw), normalized));
    assertEquals("/api/sys/dict/getDictItems/dcs_currency,code,code,=1", rebuilt.getRawPath());
  }

  @Test
  void queryStringIsLeftUntouched() {
    URI original = URI.create(HOST + "/sys/x%20y?a=%20&b=1");
    URI rebuilt =
        assertDoesNotThrow(() -> InternalSysApiBlockWebFilter.rebuildUri(original, "/sys/x y"));
    assertEquals("/sys/x%20y", rebuilt.getRawPath());
    assertEquals("a=%20&b=1", rebuilt.getRawQuery());
  }

  // ---------- deny rules must keep working ----------

  @Test
  void obfuscatedInternalPathsAreStillBlocked() {
    assertTrue(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/api/sys/api/getDictItems")));
    assertTrue(
        InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/api/sys//api/./getDictItems")));
    assertTrue(
        InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/api/sys/%2e%2e/sys/api/getDictItems")));
    assertTrue(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/api/sys/api/;jsp/getDictItems")));
    assertTrue(
        InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/api/jeecg-system/sys/api/getDictItems")));
  }

  @Test
  void denyKeywordsAndActuatorAreStillBlocked() {
    for (String p :
        new String[] {
          "/sys/swagger-resources/x",
          "/v3/api-docs/swagger-config",
          "/druid/index.html",
          "/doc.html",
          "/actuator/env",
          "/sys/getEncryptedString"
        }) {
      assertTrue(InternalSysApiBlockWebFilter.isBlocked(p), p + " must be blocked");
    }
  }

  @Test
  void exactHealthProbeIsAllowedButOtherActuatorPathsStayBlocked() {
    // M-1: allow only the exact /actuator/health (probe); the /api prefix is injected by Nginx
    assertFalse(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/actuator/health")));
    assertFalse(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/api/actuator/health")));
    // All other actuator paths and normalization bypasses must stay blocked
    assertTrue(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/actuator")));
    assertTrue(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/actuator/env")));
    assertTrue(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/actuator/health/liveness")));
    assertTrue(InternalSysApiBlockWebFilter.isBlocked(gatewayPath("/actuator/health/../env")));
  }

  @Test
  void ordinaryDictPathsAreNotBlocked() {
    assertFalse(
        InternalSysApiBlockWebFilter.isBlocked(
            gatewayPath("/api/sys/dict/getDictItems/dcs_currency,code,code")));
  }
}