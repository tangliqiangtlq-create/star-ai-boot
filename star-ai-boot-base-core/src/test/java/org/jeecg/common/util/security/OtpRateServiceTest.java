package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Gate evidence for the DCS Redis 7.0 Cluster OTP rate-limit fix (deployment gate A7 + ramp_B B8).
 *
 * <p>{@code CROSSSLOT} regression guard: all three Lua keys must carry the same {@code {otp}} hash
 * tag.
 */
class OtpRateServiceTest {

  private StringRedisTemplate tpl;
  private OtpRateService svc;

  @BeforeEach
  void setUp() throws Exception {
    tpl = mock(StringRedisTemplate.class);
    svc = new OtpRateService();
    Field f = OtpRateService.class.getDeclaredField("stringRedisTemplate");
    f.setAccessible(true);
    f.set(svc, tpl);
    svc.init();
  }

  @Test
  @SuppressWarnings("unchecked")
  void allKeysShareClusterHashTag() {
    when(tpl.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0L);

    OtpRateService.RateResult r = svc.checkAndReserve("a@b.com", "1.2.3.4");

    assertTrue(r.allowed);
    assertEquals(0, r.code);

    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(tpl)
        .execute(any(RedisScript.class), captor.capture(), any(), any(), any(), any(), any(), any());
    List<String> keys = captor.getValue();
    assertEquals(3, keys.size());
    for (String k : keys) {
      assertTrue(k.contains("{otp}"), "key must carry the {otp} hash tag, but was: " + k);
    }
  }
}
