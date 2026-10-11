package org.jeecg.common.aspect;

import cn.dev33.satoken.exception.NotSafeException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.RequireSecondAuth;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.aspect.security.SecondAuthFactorGuard;
import org.jeecg.common.constant.CommonConstant;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Secondary authentication aspect for {@link RequireSecondAuth} without amountExpression.
 *
 * <p>Amount-based endpoints are owned by {@link DynamicSecondAuthAspect}.
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class SecondAuthAspect {

  @Around("@annotation(org.jeecg.common.aspect.annotation.RequireSecondAuth)")
  public Object checkSecondAuth(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    RequireSecondAuth annotation = method.getAnnotation(RequireSecondAuth.class);

    if (annotation == null) {
      return joinPoint.proceed();
    }

    // Amount-based: DynamicSecondAuthAspect owns factor check (annotation.value() default is
    // VERIFICATION_CODE_ONLY and must not override PAYMENT_PASSWORD_ONLY for small amounts).
    if (StrUtil.isNotBlank(annotation.amountExpression())) {
      return joinPoint.proceed();
    }

    SecondAuthType authType = annotation.value();
    String service = annotation.service();
    if (StrUtil.isBlank(service)) {
      service = method.getDeclaringClass().getName() + "." + method.getName();
    }

    String userId = StpUtil.getLoginIdAsString();
    try {
      StpUtil.checkSafe(service);
      String factorReject = SecondAuthFactorGuard.rejectReasonIfIncompatible(service, authType);
      if (factorReject != null) {
        return Result.error(CommonConstant.SC_SECOND_AUTH_REQUIRED, factorReject);
      }
      log.debug(
          "User [{}] passed secondary authentication, service: {}, auth type: {}",
          userId,
          service,
          authType);
      return joinPoint.proceed();
    } catch (NotSafeException e) {
      log.warn(
          "User [{}] failed second authentication, service: {}, auth type: {}",
          userId,
          service,
          authType);
      return Result.error(CommonConstant.SC_SECOND_AUTH_REQUIRED, getErrorMessage(authType));
    }
  }

  private String getErrorMessage(SecondAuthType authType) {
    switch (authType) {
      case VERIFICATION_CODE_ONLY:
        return "Verification code second authentication required; please call /sys/openSafeWithAuth first";
      case PAYMENT_PASSWORD_ONLY:
        return "Payment password second authentication required; please call /sys/openSafeWithAuth first";
      case BOTH_REQUIRED:
        return "Verification code and payment password dual authentication required; please call /sys/openSafeWithAuth first";
      case EMAIL_PASSWORD_REQUIRED:
        return "Email verification code and login password dual authentication required; please call /sys/openSafeWithAuth first";
      default:
        return "Second authentication required; please call /sys/openSafeWithAuth first";
    }
  }
}
