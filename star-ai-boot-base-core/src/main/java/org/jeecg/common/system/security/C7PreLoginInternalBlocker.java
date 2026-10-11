package org.jeecg.common.system.security;

import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class C7PreLoginInternalBlocker {

  private static final Set<String> BLOCKED_INTERACTIVE_USERNAMES =
      Set.of(
          "internal_call",
          "sa-token-inner",
          "inner_s2s",
          "system",
          "processengine_s2s",
          "dcs_s2s_read",
          "dcs_s2s_write");

  public static void assertAllowedForInteractiveLogin(String username) {
    if (username == null) {
      return;
    }
    String trimmed = username.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    String u = trimmed.toLowerCase(Locale.ROOT);
    for (String blocked : BLOCKED_INTERACTIVE_USERNAMES) {
      if (u.contains(blocked)) {
        log.error(
            "SEC-C7-INTERNAL-BLOCK interactive-login blocked username={} blockedMatch={}",
            username,
            blocked);
        throw new JeecgBootException(
            "C7 ACCOUNT_DISABLED: system internal accounts cannot login via interactive flows; "
                + "S2S only via InternalTrustedS2sFilter with X-Internal-Call-Secret + CIDR. blocked="
                + username,
            403);
      }
    }
  }
}
