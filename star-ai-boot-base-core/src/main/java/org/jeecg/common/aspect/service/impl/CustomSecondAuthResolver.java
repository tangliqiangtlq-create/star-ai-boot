package org.jeecg.common.aspect.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.aspect.service.SecondAuthTypeResolver;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Example custom secondary-auth type resolver.
 *
 * <p>Implement {@link SecondAuthTypeResolver} for business-specific rules, for example:
 * <ul>
 *   <li>Transaction amount</li>
 *   <li>Operation type</li>
 *   <li>User level</li>
 *   <li>Time of day (e.g. require 2FA at night)</li>
 *   <li>Client IP (e.g. require 2FA for remote login)</li>
 * </ul>
 *
 * <p>Usage:
 * <ol>
 *   <li>Implement {@link SecondAuthTypeResolver}</li>
 *   <li>Encode business logic in {@code resolveAuthType}</li>
 *   <li>Register as a Spring bean ({@code @Component})</li>
 *   <li>The system invokes all registered resolvers automatically</li>
 * </ol>
 *
 * <p>Note: sample only — adapt or remove for production.
 *
 * @author jeecg-boot
 */
@Slf4j
@Component
public class CustomSecondAuthResolver implements SecondAuthTypeResolver {

  @Override
  public SecondAuthType resolveAuthType(HttpServletRequest request, String service) {
    // Example 1: by operation type
    String operationType = request.getParameter("operationType");
    if ("DELETE_ACCOUNT".equals(operationType)) {
      // Deleting an account requires both factors
      return SecondAuthType.BOTH_REQUIRED;
    }

    // Example 2: by user level (load user from request/session)
    // String userLevel = getUserLevel(request);
    // if ("VIP".equals(userLevel)) {
    //   return SecondAuthType.PAYMENT_PASSWORD_ONLY;
    // }

    // Example 3: by time of day (night operations require both factors)
    // int hour = LocalDateTime.now().getHour();
    // if (hour >= 22 || hour < 6) {
    //   return SecondAuthType.BOTH_REQUIRED;
    // }

    // Example 4: by IP (remote login requires both factors)
    // String ip = getClientIp(request);
    // if (isRemoteLocation(ip)) {
    //   return SecondAuthType.BOTH_REQUIRED;
    // }

    // No match: return null so other resolvers / config can handle it
    return null;
  }

  @Override
  public boolean requiresSecondAuth(HttpServletRequest request, String service) {
    // Sample only — must stay off for production paths or it runs on every controller call.
    return false;
  }
}
