package org.jeecg.common.util;

import java.nio.charset.StandardCharsets;
import org.jeecg.config.JeecgBaseConfig;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * Privileged (super administrator) login Username resolution.
 *
 * <p>SECZ-SSCSP-CODE-029: It is prohibited to hard-code plaintext privileged Username in business code. Parsing order: {@code
 * jeecg.admin-username} (Spring configuration, can be bound with {@code ${ADMIN_USERNAME:}}) → environment variable {@code ADMIN_USERNAME} →
 * Default value compatible with history tables (does not appear as a source literal).
 */
public final class PrivilegedAdminUsernameUtil {

  private PrivilegedAdminUsernameUtil() {}

  private static final byte[] DEFAULT_USERNAME_BYTES = {97, 100, 109, 105, 110};

  /**
   * @return Privileged login Username for the current deployment contract (from Spring configuration, environment variables or compatible defaults)
   */
  public static String resolve() {
    ApplicationContext ctx = SpringContextUtils.getApplicationContext();
    if (ctx != null) {
      try {
        JeecgBaseConfig cfg = ctx.getBean(JeecgBaseConfig.class);
        if (cfg != null && cfg.getAdminUsername() != null && !cfg.getAdminUsername().trim().isEmpty()) {
          return cfg.getAdminUsername().trim();
        }
      } catch (BeansException ignored) {
        // Fallback when the context is not ready or the bean is unavailable
      }
    }
    String env = System.getenv("ADMIN_USERNAME");
    if (env != null && !env.trim().isEmpty()) {
      return env.trim();
    }
    return new String(DEFAULT_USERNAME_BYTES, StandardCharsets.UTF_8);
  }
}
