package org.jeecg.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.web.cors.CorsConfiguration;

/**
 * A6: exact Origin allow-list must accept admin host; ^/$ patterns must not be fed into Spring
 * {@code allowedOriginPatterns} (they become literals → empty-body 403).
 */
public class GatewayCorsConfigurationTest {

  @Test
  public void anchorHelper_keepsCaretDollarAndEscapedDots() {
    String anchored =
        GatewayCorsConfiguration.anchorAndEscapeOriginPattern("https://mpay-admin.xnow.xyz");
    assertEquals("^https://mpay-admin\\.xnow\\.xyz$", anchored);
  }

  @Test
  public void exactAllowedOrigins_acceptsAdmin_rejectsEvilSubstring() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(Collections.singletonList("https://mpay-admin.xnow.xyz"));
    assertEquals("https://mpay-admin.xnow.xyz", cfg.checkOrigin("https://mpay-admin.xnow.xyz"));
    assertNull(cfg.checkOrigin("https://attacker-mpay-admin.xnow.xyz.evil.ru"));
    assertNull(cfg.checkOrigin("https://evil.example"));
  }

  @Test
  public void springOriginPatterns_withRegexAnchors_neverMatchRealOrigin() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOriginPatterns(
        Collections.singletonList(
            GatewayCorsConfiguration.anchorAndEscapeOriginPattern(
                "https://mpay-admin.xnow.xyz")));
    // Regression: previous A6 wiring fed ^...$ into OriginPattern → always null.
    assertNull(cfg.checkOrigin("https://mpay-admin.xnow.xyz"));
  }

  @Test
  public void validateExactOrigin_rejectsWildcard() {
    BeanInitializationException ex =
        assertThrows(
            BeanInitializationException.class,
            () -> GatewayCorsConfiguration.validateExactOrigin("https://*.xnow.xyz"));
    assertTrue(ex.getMessage().contains("[A6]"));
    assertNotNull(
        GatewayCorsConfiguration.anchorAndEscapeOriginPattern("https://mpay.xnow.xyz"));
    GatewayCorsConfiguration.validateExactOrigin("https://mpay-admin.xnow.xyz");
  }
}
