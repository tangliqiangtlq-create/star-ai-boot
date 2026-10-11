package org.jeecg.common.config.feign;

/**
 * Infinite-loop protection when retrying Feign authentication.
 */
public final class FeignAuthRetrySupport {

  private static final ThreadLocal<Boolean> SKIP_AUTH_RETRY =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

  private FeignAuthRetrySupport() {}

  public static boolean isSkipAuthRetry() {
    return Boolean.TRUE.equals(SKIP_AUTH_RETRY.get());
  }

  public static void runWithSkipAuthRetry(Runnable action) {
    SKIP_AUTH_RETRY.set(Boolean.TRUE);
    try {
      action.run();
    } finally {
      SKIP_AUTH_RETRY.remove();
    }
  }

  public static void clearSkipAuthRetryForTests() {
    SKIP_AUTH_RETRY.remove();
  }
}
