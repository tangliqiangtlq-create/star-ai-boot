package org.jeecg.common.aspect;

import cn.dev33.satoken.exception.NotSafeException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jeecg.common.api.vo.Result;
import cn.hutool.core.util.StrUtil;
import java.lang.reflect.Method;
import org.aspectj.lang.reflect.MethodSignature;
import org.jeecg.common.aspect.annotation.RequireSecondAuth;
import org.jeecg.common.aspect.config.SecondAuthConfig;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.aspect.security.SecondAuthFactorGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Configuration-based second-factor authaspect The second-factor authtype of the unified management interface through the configurationfile
 *
 * @author jeecg-boot
 */
@Slf4j
@Aspect
@Component
@Order(2) // Execute after annotationaspect
public class ConfigBasedSecondAuthAspect {

  @Autowired private SecondAuthConfig secondAuthConfig;

  /** Block all Controllermethods, checkwhether requires second-factor auth if method already has@ RequireSecondAuthannotation, skip (by SecondAuthAspectprocess) */
  @Around(
      "execution(* org.jeecg.modules..controller..*(..)) && "
          + "!@annotation(org.jeecg.common.aspect.annotation.RequireSecondAuth)")
  public Object checkConfigBasedSecondAuth(ProceedingJoinPoint joinPoint) throws Throwable {
    // If configuration is not enabled, release directly
    if (!secondAuthConfig.isEnabled()) {
      return joinPoint.proceed();
    }

    // getrequestpath
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return joinPoint.proceed();
    }

    HttpServletRequest request = attributes.getRequest();
    String requestPath = request.getRequestURI();

    // checkwhether requires second-factor auth
    if (!secondAuthConfig.requiresSecondAuth(requestPath)) {
      return joinPoint.proceed();
    }

    // get authentication type
    SecondAuthType authType = secondAuthConfig.getAuthType(requestPath);
    String service = requestPath; // Use requestpath as service identity

    // Try annotationgetservice from
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    RequireSecondAuth annotation = method.getAnnotation(RequireSecondAuth.class);
    if (annotation != null && StrUtil.isNotBlank(annotation.service())) {
      service = annotation.service();
    }

    // Check whether secondary authentication has already passed
    try {
      StpUtil.checkSafe(service);
      String factorReject = SecondAuthFactorGuard.rejectReasonIfIncompatible(service, authType);
      if (factorReject != null) {
        return Result.error(org.jeecg.common.constant.CommonConstant.SC_SECOND_AUTH_REQUIRED, factorReject);
      }
      log.debug("User passed second authentication, path: {}, auth type: {}, service: {}", requestPath, authType, service);
      return joinPoint.proceed();
    } catch (NotSafeException e) {
      // Secondary authentication failed; return error message
      log.warn("User failed second authentication, path: {}, auth type: {}, service: {}", requestPath, authType,service);

      String errorMessage = getErrorMessage(authType);
      return Result.error(org.jeecg.common.constant.CommonConstant.SC_SECOND_AUTH_REQUIRED, errorMessage);
    }
  }

  /** Get error message by authentication type */
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
