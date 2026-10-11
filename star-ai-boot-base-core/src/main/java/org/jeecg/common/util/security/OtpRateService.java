package org.jeecg.common.util.security;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OtpRateService {

  /**
   * Redis Cluster hash tag. All three rate-limit keys MUST share the same hash tag, otherwise the
   * multi-key Lua script is rejected with {@code CROSSSLOT Keys in request don't hash to the same
   * slot} (DCS Redis 7.0 Cluster, see deployment gate A7).
   */
  private static final String SLOT_TAG = "{otp}";

  @Autowired private StringRedisTemplate stringRedisTemplate;

  private DefaultRedisScript<Long> otpRateScript;

  @PostConstruct
  public void init() {
    otpRateScript = new DefaultRedisScript<>();
    otpRateScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("otp_send_rate.lua")));
    otpRateScript.setResultType(Long.class);
  }

  public static class RateResult {
    public final boolean allowed;
    public final int code;

    public RateResult(boolean allowed, int code) {
      this.allowed = allowed;
      this.code = code;
    }
  }

  /**
   * per-email 1/60s, per-ip 10/3600s, global 1000/60s
   *
   * @return RateResult.code: 0=allowed, 1=per-email exceeded, 2=per-ip exceeded, 3=global exceeded
   */
  public RateResult checkAndReserve(String email, String ip) {
    String emailKey = "otp_rate:" + SLOT_TAG + ":email:" + (email == null ? "null" : email.toLowerCase().trim());
    String ipKey = "otp_rate:" + SLOT_TAG + ":ip:" + (ip == null ? "unknown" : ip.trim());
    String globalKey = "otp_rate:" + SLOT_TAG + ":global";
    try {
      List<String> keys = Arrays.asList(emailKey, ipKey, globalKey);
      Long result =
          stringRedisTemplate.execute(
              otpRateScript, keys, "1", "60", "10", "3600", "1000", "60");
      long r = result == null ? 0L : result;
      return new RateResult(r == 0L, (int) r);
    } catch (Exception e) {
      log.warn("OtpRateService lua execute fail, fallback allow email={} ip={}", email, ip, e);
      return new RateResult(true, 0);
    }
  }
}
