package org.jeecg.common.aspect.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.jeecg.common.aspect.annotation.RequireSecondAuth;
import org.jeecg.common.aspect.enumeration.SecondAuthType;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * second-factor authtypeparse interface
 * 
 * Used to dynamically determine the secondary Authentication type based on business conditions
 * Implementing this interface can dynamically select the authentication method based on business logic (such as Transaction amount, operation Type, etc.) in the code.
 * 
 * @author jeecg-boot
 */
public interface SecondAuthTypeResolver {

  /**
   * Parse secondary Authentication type based on request information
   * 
   * @param request HTTP request object
   * @param service service identifier
   * @return Secondary auth type; if null, use default or annotation config
   */
  SecondAuthType resolveAuthType(HttpServletRequest request, String service);

  /**
   * Parse secondary Authentication type based on request information and method information (supports expressions)
   * 
   * @param request HTTP request object
   * @param service service identifier
   * @param joinPoint Join point info including method args
   * @param method Method object
   * @param annotation Annotation info
   * @return Secondary auth type; if null, use default or annotation config
   */
  default SecondAuthType resolveAuthType(HttpServletRequest request, String service,
                                         ProceedingJoinPoint joinPoint, Method method,
                                         RequireSecondAuth annotation) {
    // Default implementation: call the original method
    return resolveAuthType(request, service);
  }

  /**
   * Determine whether secondary authentication is required
   * 
   * @param request HTTP request object
   * @param service service identifier
   * @return true if secondary auth is required, false otherwise
   */
  default boolean requiresSecondAuth(HttpServletRequest request, String service) {
    SecondAuthType authType = resolveAuthType(request, service);
    return authType != null;
  }

  /**
   * Determine whether secondary authentication is required (supports expressions)
   * 
   * @param request HTTP request object
   * @param service service identifier
   * @param joinPoint Join point info including method args
   * @param method method object
   * @param annotation annotation information
   * @return true means secondary authentication is required, false means no
   */
  default boolean requiresSecondAuth(HttpServletRequest request, String service,
                                     ProceedingJoinPoint joinPoint, Method method,
                                     RequireSecondAuth annotation) {
    // Default implementation: Call a method that supports method information
    SecondAuthType authType = resolveAuthType(request, service, joinPoint, method, annotation);
    return authType != null;
  }
}

