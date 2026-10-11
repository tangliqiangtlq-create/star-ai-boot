package org.jeecg.common.aspect;

import cn.dev33.satoken.exception.NotSafeException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import java.lang.reflect.Method;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.RequireSecondAuth;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.aspect.security.SecondAuthFactorGuard;
import org.jeecg.common.aspect.service.SecondAuthTypeResolver;
import org.jeecg.common.constant.CommonConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Dynamic secondary authentication (amount / business resolvers).
 *
 * <p>Owns endpoints with {@code amountExpression}; uses code 412 (not 401) so portal does not
 * treat challenges as session expiry.
 */
@Slf4j
@Aspect
@Component
@Order(0)
public class DynamicSecondAuthAspect {

  @Autowired(required = false)
  private List<SecondAuthTypeResolver> authTypeResolvers;

  @Around("execution(* org.jeecg.modules..controller..*(..))")
  public Object checkDynamicSecondAuth(ProceedingJoinPoint joinPoint) throws Throwable {
    if (authTypeResolvers == null || authTypeResolvers.isEmpty()) {
      return joinPoint.proceed();
    }

    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return joinPoint.proceed();
    }

    HttpServletRequest request = attributes.getRequest();
    String requestPath = request.getRequestURI();

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    RequireSecondAuth annotation = method.getAnnotation(RequireSecondAuth.class);

    SecondAuthType authType = null;
    String service = requestPath;
    if (annotation != null && StrUtil.isNotBlank(annotation.service())) {
      service = annotation.service();
    }

    for (SecondAuthTypeResolver resolver : authTypeResolvers) {
      if (resolver.requiresSecondAuth(request, service, joinPoint, method, annotation)) {
        authType = resolver.resolveAuthType(request, service, joinPoint, method, annotation);
        if (authType != null) {
          log.debug(
              "Dynamic resolver [{}] returned auth type: {}, path: {}",
              resolver.getClass().getSimpleName(),
              authType,
              requestPath);
          break;
        }
      }
    }

    // Amount-based endpoints: fail closed if type cannot be resolved (SecondAuthAspect skips them).
    if (authType == null
        && annotation != null
        && StrUtil.isNotBlank(annotation.amountExpression())) {
      log.warn(
          "Amount second-auth unresolved: path={} service={} amountExpression={}",
          requestPath,
          service,
          annotation.amountExpression());
      return Result.error(
          CommonConstant.SC_SECOND_AUTH_REQUIRED,
          "Second authentication required; cannot resolve auth type for this amount/account; please call /sys/openSafeWithAuth first");
    }

    if (authType == null) {
      return joinPoint.proceed();
    }

    try {
      StpUtil.checkSafe(service);
      String factorReject = SecondAuthFactorGuard.rejectReasonIfIncompatible(service, authType);
      if (factorReject != null) {
        return Result.error(CommonConstant.SC_SECOND_AUTH_REQUIRED, factorReject);
      }
      log.debug(
          "User passed second authentication, path: {}, dynamic auth type: {}, service: {}",
          requestPath,
          authType,
          service);
      return joinPoint.proceed();
    } catch (NotSafeException e) {
      log.warn(
          "User failed second authentication, path: {}, dynamic auth type: {}, service: {}",
          requestPath,
          authType,
          service);
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
