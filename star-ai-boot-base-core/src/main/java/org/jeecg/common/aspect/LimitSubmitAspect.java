package org.jeecg.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import java.lang.reflect.Method;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.aspect.annotation.LimitSubmit;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.LocaleResolver;
import org.jeecg.common.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.stereotype.Component;

@Component
@Aspect
@Slf4j
public class LimitSubmitAspect {
  // Encapsulates various redis operation methods
  @Autowired private RedisUtil redisUtil;

  @Autowired private MessageSource messageSource;

  @Pointcut("@annotation(org.jeecg.common.aspect.annotation.LimitSubmit)")
  private void pointcut() {}

  @Around("pointcut()")
  public Object handleSubmit(ProceedingJoinPoint joinPoint) throws Throwable {

    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    // Get annotation information
    LimitSubmit limitSubmit = method.getAnnotation(LimitSubmit.class);

    LoginUser loginUser;

    if (StpUtil.isLogin()) {
      loginUser = (LoginUser) StpUtil.getSession().get("loginUser");
    } else {
      loginUser = new LoginUser();
    }

    int submitTimeLimiter = limitSubmit.limit();
    String redisKey = limitSubmit.key();
    boolean needAllWait = limitSubmit.needAllWait();
    String key = getRedisKey(loginUser, joinPoint, redisKey);
    Object result = redisUtil.get(key);
    if (result != null) {
      Locale locale = LocaleResolver.getCurrLocale();
      log.error("{} limit submit", key);
      throw new JeecgBootException(
          messageSource.getMessage("common.LimitSubmit.limit", null, locale));
    }
    redisUtil.set(key, true, submitTimeLimiter);
    try {
      return joinPoint.proceed();
    } catch (Throwable e) {
      log.error(
          "Exception in {}.{}() with cause = \'{}\' and exception = \'{}\'",
          joinPoint.getSignature().getDeclaringTypeName(),
          joinPoint.getSignature().getName(),
          e.getCause() != null ? e.getCause() : "NULL",
          e.getMessage(),
          e);
      throw e;
    } finally {
      if (!needAllWait) {
        // The formatted key (including userId) must be deleted, and the template redisKey in the annotation cannot be deleted.
        redisUtil.del(key);
      }
    }
  }

  /** Supports multiple parameters, processed from Request parameters */
  private String getRedisKey(LoginUser sysUser, ProceedingJoinPoint joinPoint, String key) {
    if (key.contains("%s")) {
      key = String.format(key, sysUser.getId());
    }
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

    DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    String[] parameterNames = discoverer.getParameterNames(method);
    if (parameterNames != null) {
      for (int i = 0; i < parameterNames.length; i++) {
        String item = parameterNames[i];
        if (key.contains("#" + item)) {
          key = key.replace("#" + item, joinPoint.getArgs()[i].toString());
        }
      }
    }
    return key;
  }
}
