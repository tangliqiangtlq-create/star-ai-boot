package org.jeecg.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * M-1: regression for the exact allow-list of /actuator/health at the gateway deny layer.
 *
 * <p>Only the exact probe path is allowed; all other /actuator/** and internal paths must stay 403.
 */
class InternalPathDenyFilterTest {

  @Test
  void exactHealthProbeIsAllowed() {
    assertFalse(InternalPathDenyFilter.isBlocked("/actuator/health"));
    // Nginx injects the /api prefix; after normalize it is still the same probe path
    assertFalse(InternalPathDenyFilter.isBlocked(InternalPathDenyFilter.normalize("/api/actuator/health")));
    // An optional semicolon matrix parameter must not change the decision
    assertFalse(InternalPathDenyFilter.isBlocked(InternalPathDenyFilter.normalize("/actuator/health;x")));
  }

  @Test
  void otherActuatorPathsStayBlocked() {
    assertTrue(InternalPathDenyFilter.isBlocked("/actuator"));
    assertTrue(InternalPathDenyFilter.isBlocked("/actuator/env"));
    assertTrue(InternalPathDenyFilter.isBlocked("/actuator/health/liveness"));
    assertTrue(InternalPathDenyFilter.isBlocked("/actuator/health/.."));
    assertTrue(InternalPathDenyFilter.isBlocked("/actuator/./health"));
    assertTrue(InternalPathDenyFilter.isBlocked("/actuator/%65nv"));
  }

  @Test
  void internalPathsStayBlocked() {
    assertTrue(InternalPathDenyFilter.isBlocked("/sys/api/x"));
    assertTrue(InternalPathDenyFilter.isBlocked("/druid/index.html"));
    assertTrue(InternalPathDenyFilter.isBlocked("/doc.html"));
  }
}
