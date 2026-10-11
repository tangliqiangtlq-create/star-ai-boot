package org.jeecg.common.aspect;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FailCountLimit {
  int maxFailures() default 5; // Maximum number of failures

  long lockDuration() default 3600; // Lock duration (seconds)

  /** Sliding window for fail_count:* key TTL (CodePlan 36 / C2-3). Default 15 minutes. */
  long windowSeconds() default 900;

  /**
   * SpEL expression appended as an extra dimension to enforce per-challenge/per-nonce
   * throttling (avoids VA-Case-4-style TTL-infinite-brute). Example:
   * <pre>@FailCountLimit(..., keyExpression = "#challenge_id")</pre>
   * or
   * <pre>@FailCountLimit(..., keyExpression = "#nonce + ':' + #service")</pre>
   */
  String keyExpression() default "";
}
