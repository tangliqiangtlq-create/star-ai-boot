package org.jeecg.common.aspect.annotation;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.CodeSignature;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.FailCountLimit;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.IpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class FailCountLimitAspect {

  @Autowired private StringRedisTemplate redisTemplate;

  private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

  private static String currentLoginIdOrDefault(String fallback) {
    try {
      String uid = StpUtil.getLoginIdAsString();
      if (StrUtil.isNotBlank(uid)) return "u:" + uid.replace(':', '_');
    } catch (Exception ignore) {
    }
    return fallback;
  }

  private static String evalKeyExpression(String expr, JoinPoint jp) {
    try {
      if (StrUtil.isBlank(expr)) return null;
      CodeSignature sig = (CodeSignature) jp.getSignature();
      String[] names = sig.getParameterNames();
      Object[] args = jp.getArgs();
      StandardEvaluationContext ctx = new StandardEvaluationContext();
      for (int i = 0; i < names.length; i++) {
        ctx.setVariable(names[i], args[i]);
      }
      Expression exp = SPEL_PARSER.parseExpression(expr);
      Object v = exp.getValue(ctx);
      return v == null ? null : String.valueOf(v);
    } catch (Exception ignore) {
      return null;
    }
  }

  private String[] resolveKeys(JoinPoint joinPoint, FailCountLimit failCountLimit) {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    String ip = attributes == null ? "unknown" : IpUtils.getIpAddr(attributes.getRequest());
    String methodName = joinPoint.getSignature().getName();
    String dim = currentLoginIdOrDefault("i:" + ip);
    if (failCountLimit != null) {
      String extra = evalKeyExpression(failCountLimit.keyExpression(), joinPoint);
      if (StrUtil.isNotBlank(extra)) {
        dim = dim + ":c:" + extra.replace(':', '_');
      }
    }
    String countKey = "fail_count:" + dim + ":" + methodName;
    String lockKey = "locked_ip:" + dim + ":" + methodName;
    return new String[] {countKey, lockKey};
  }

  @Before("@annotation(failCountLimit)")
  public void before(JoinPoint joinPoint, FailCountLimit failCountLimit) {
    String[] keys = resolveKeys(joinPoint, failCountLimit);
    String lockKey = keys[1];
    if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
      throw new JeecgBootException("Too many failed attempts. Please try again later.");
    }
  }

  @Around("@annotation(failCountLimit)")
  public Object around(ProceedingJoinPoint pjp, FailCountLimit failCountLimit) throws Throwable {
    String[] keys = resolveKeys(pjp, failCountLimit);
    String countKey = keys[0];
    String lockKey = keys[1];
    long window = failCountLimit.windowSeconds() > 0 ? failCountLimit.windowSeconds() : 900L;
    try {
      String current = redisTemplate.opsForValue().get(countKey);
      int failCount = current == null ? 0 : Integer.parseInt(current);
      if (failCount >= failCountLimit.maxFailures()) {
        redisTemplate
            .opsForValue()
            .set(lockKey, "1", failCountLimit.lockDuration(), TimeUnit.SECONDS);
        redisTemplate.delete(countKey);
        throw new JeecgBootException("Too many failed attempts. Please try again later.");
      }
    } catch (JeecgBootException e) {
      throw e;
    } catch (Exception ignore) {
    }
    try {
      // Accounting happens in afterReturning (business Result.error) or the catch below
      // (thrown exception). Do NOT clear counters here: this advice wraps proceed(), so it
      // resumes AFTER afterReturning has run, and clearing here would erase the failure just
      // recorded — the limit would never arm (fail-open).
      return pjp.proceed();
    } catch (Throwable t) {
      recordFailure(countKey, lockKey, failCountLimit, window);
      throw t;
    }
  }

  @AfterReturning(value = "@annotation(failCountLimit)", returning = "result")
  public void afterReturning(JoinPoint joinPoint, FailCountLimit failCountLimit, Object result) {
    String[] keys = resolveKeys(joinPoint, failCountLimit);
    String countKey = keys[0];
    String lockKey = keys[1];
    long window = failCountLimit.windowSeconds() > 0 ? failCountLimit.windowSeconds() : 900L;
    if (isBusinessFailure(result)) {
      recordFailure(countKey, lockKey, failCountLimit, window);
      return;
    }
    try {
      redisTemplate.delete(countKey);
      redisTemplate.delete(lockKey);
    } catch (Exception ignore) {
    }
  }

  private void recordFailure(
      String countKey, String lockKey, FailCountLimit failCountLimit, long window) {
    try {
      // Already locked: further increments are pointless and would only re-arm the lock window.
      if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
        return;
      }
      Long count = redisTemplate.opsForValue().increment(countKey, 1L);
      if (count != null && count == 1L) {
        redisTemplate.expire(countKey, window, TimeUnit.SECONDS);
      }
      if (count != null && count >= failCountLimit.maxFailures()) {
        redisTemplate
            .opsForValue()
            .set(lockKey, "1", failCountLimit.lockDuration(), TimeUnit.SECONDS);
        redisTemplate.delete(countKey);
      }
    } catch (Exception ignore) {
    }
  }

  /**
   * Business failures are signalled by a non-throwing {@code Result.error(...)} — e.g.
   * {@code /sys/openSafeWithAuth} answers "authentication failed" instead of throwing. Without
   * counting those, the counter was cleared on every attempt and the limit never armed.
   */
  private static boolean isBusinessFailure(Object result) {
    return result instanceof Result && !((Result<?>) result).isSuccess();
  }

  @AfterThrowing(value = "@annotation(failCountLimit)", throwing = "ex")
  public void afterThrowing(JoinPoint joinPoint, FailCountLimit failCountLimit, Exception ex) {
  }
}
