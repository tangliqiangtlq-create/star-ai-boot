package org.jeecg.common.aspect.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.aspect.annotation.RequireSecondAuth;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.jeecg.common.aspect.service.SecondAuthTypeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.beans.PropertyDescriptor;

/**
 * second-factor authtypeparse based on account type and transaction amount
 *
 * <p>Determine the second-factor authtype dynamically based on the account type and transaction amount: - Small transactions (&lt; threshold): Pay password only - Large transactions (&gt; = threshold): Pay password + verification code Two-factor authentication
 *
 * <p>the amount threshold supports different thresholds by account typeconfiguration through dictionarysystemconfiguration. Each get threshold is loaded from the dictionary in real time, ensuring the real-time nature of the configuration.
 *
 * <p>Usage: 1. In dictionarysystem configurationamount threshold (dictionary code: transaction_amount_threshold) - value of dictionary item: value of account type (e.g.
 * "withdrawal", "conversation", etc.) - the text: amount threshold of the dictionary item (e.g. "10000", "5000", etc., in yuan) 2. Inconfiguration expression in @ RequireSecondAuth annotation (required)
 * - amountExpression: amount expression (SpEL expression, required) - accountTypeExpression: account type expression (SpEL expression, optional)
 *
 * <p>dictionaryconfiguration example (configuration in systemdictionary management): dictionary code: transaction_amount_threshold dictionary item: -value: withdrawal, text:
 * 10000 (withdrawal account) - value: consumption account, text: 5000 (consumption account) - value: deposit, text: 20000 (deposit account) -
 * value: financial, text: 50,000 (financial account)
 *
 * <p>example annotation:@RequireSecondAuth( amountExpression = "#withdrawalRecord.amount", accountTypeExpression = "#withdrawalRecord.accountType",
 * service = "manualWithdrawal" ) @RequireSecondAuth( amountExpression = "#amount", accountTypeExpression = "#accountType" )
 * @RequireSecondAuth( amountExpression = "#args[0].amount", accountTypeExpression = "#args[0].accountType" )
 *
 * @author jeecg-boot
 */
@Slf4j
@Component
public class AmountBasedSecondAuthResolver implements SecondAuthTypeResolver {

  @Autowired private CommonAPI commonAPI;

  /** SpEL expression parse wrapper */
  private static final ExpressionParser parser = new SpelExpressionParser();

  @Override
  public SecondAuthType resolveAuthType(HttpServletRequest request, String service) {
    // Only expression methods are supported, this method no longer gets from the request parameter
    return null;
  }

  @Override
  public SecondAuthType resolveAuthType(
      HttpServletRequest request,
      String service,
      ProceedingJoinPoint joinPoint,
      Method method,
      RequireSecondAuth annotation) {
    // Only expression methods are supported, amountExpression is required
    if (annotation == null || !StringUtils.hasText(annotation.amountExpression())) {
      log.debug("Amount expression not provided, skipping");
      return null;
    }

    try {
      // getamount (required)
      BigDecimal amount =
          parseAmountFromExpression(annotation.amountExpression(), joinPoint, method, request);
      if (amount == null) {
        Object[] args = joinPoint.getArgs();
        log.warn(
            "Amount expression {} parsed empty; method={} paramNames={} argsLen={} arg0={}",
            annotation.amountExpression(),
            method.getName(),
            java.util.Arrays.toString(getParameterNames(method)),
            args == null ? -1 : args.length,
            args != null && args.length > 0 && args[0] != null
                ? args[0].getClass().getSimpleName()
                : null);
        return null;
      }

      // get account type (optional)
      String accountType = null;
      if (StringUtils.hasText(annotation.accountTypeExpression())) {
        accountType =
            parseAccountTypeFromExpression(
                annotation.accountTypeExpression(), joinPoint, method, request);
      }

      return resolveAuthTypeByAccountTypeAndAmount(accountType, amount, service);
    } catch (Exception e) {
      log.warn("Failed to parse expression, error: {}");
      return null;
    }
  }

  /**
   * Authentication type based on account type and amount
   *
   * @ param accountType account type (can be empty, use default threshold)
   * @ param amount transaction amount
   * @param service service identifier
   * @return Authentication type
   */
  private SecondAuthType resolveAuthTypeByAccountTypeAndAmount(
      String accountType, BigDecimal amount, String service) {
    // get the threshold corresponding to the account type, if there is no configuration, use the default threshold
    BigDecimal threshold = getThresholdForAccountType(accountType);

    if (amount.compareTo(threshold) >= 0) {
      // Large transactions: 2-factor authentication required
      log.info(
          "Service: {}, account type: {}, transaction amount {} CNY >= threshold {} CNY, dual auth required",
          service,
          accountType != null ? accountType : "Default",
          amount,
          threshold);
      return SecondAuthType.BOTH_REQUIRED;
    } else {
      // Microtransactions: Pay password only
      log.info(
          "Service: {}, account type: {}, transaction amount {} CNY < threshold {} CNY, payment password auth only",
          service,
          accountType != null ? accountType : "Default",
          amount,
          threshold);
      return SecondAuthType.PAYMENT_PASSWORD_ONLY;
    }
  }

  /**
   * Public resolve for preview / openSafeWithAuth (B5-1: client must not invent requiredFactor).
   *
   * @return map with requiredFactor, threshold, accountType
   */
  public java.util.Map<String, Object> resolvePreview(String accountType, BigDecimal amount, String service) {
    java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
    if (amount == null) {
      out.put("requiredFactor", null);
      out.put("threshold", null);
      return out;
    }
    BigDecimal threshold = getThresholdForAccountType(accountType);
    SecondAuthType type = resolveAuthTypeByAccountTypeAndAmount(accountType, amount, service);
    out.put("requiredFactor", type.getCode());
    out.put("threshold", threshold);
    out.put("accountType", accountType);
    out.put("amount", amount);
    out.put("service", service);
    return out;
  }

  /**
   * Get the amount threshold of the specified Account type. It is loaded from the dictionary in real time every time it is called. If there is no configuration in the dictionary, the default threshold is used.
   *
   * @param accountType Account type (value of dictionary item)
   * @return amount threshold
   */
  private BigDecimal getThresholdForAccountType(String accountType) {
    try {
      String second_auth_amount_threshold =
          commonAPI.translateDict("transaction_amount_threshold", accountType);
      if (StringUtils.hasText(second_auth_amount_threshold)) {
        return new BigDecimal(second_auth_amount_threshold.trim());
      }
    } catch (Exception e) {
      log.warn(
          "transaction_amount_threshold missing/invalid for accountType={}, using default 100",
          accountType);
    }
    // Safe default: small amounts → payment password only (aligned with common dict values)
    return new BigDecimal("100");
  }

  /** Parse amounts from SpEL expressions */
  private BigDecimal parseAmountFromExpression(
      String expression, ProceedingJoinPoint joinPoint, Method method, HttpServletRequest request) {
    try {
      if (!isSafeExpression(expression)) {
        return null;
      }
      EvaluationContext context = createEvaluationContext(joinPoint, method, request);

      Object result = evaluateSimpleExpression(expression, joinPoint, method, request);

      if (result == null) {
        log.debug("Expression {} returned null", expression);
        return null;
      }

      // convert to BigDecimal
      if (result instanceof BigDecimal) {
        return (BigDecimal) result;
      } else if (result instanceof Number) {
        return BigDecimal.valueOf(((Number) result).doubleValue());
      } else if (result instanceof String) {
        return new BigDecimal((String) result);
      } else {
        log.warn("Expression {} return type not supported: {}", expression, result.getClass().getName());
        return null;
      }
    } catch (Exception e) {
      log.error("Failed to parse amount expression: {}", expression, e);
      return null;
    }
  }

  /** Parse Account type from SpEL expression */
  private String parseAccountTypeFromExpression(
      String expression, ProceedingJoinPoint joinPoint, Method method, HttpServletRequest request) {
    try {
      if (!isSafeExpression(expression)) {
        return null;
      }
      EvaluationContext context = createEvaluationContext(joinPoint, method, request);

      Object result = evaluateSimpleExpression(expression, joinPoint, method, request);

      if (result == null) {
        log.debug("Expression {} returned null", expression);
        return null;
      }

      // convert to String
      if (result instanceof String) {
        return (String) result;
      } else {
        return String.valueOf(result);
      }
    } catch (Exception e) {
      log.error("Failed to parse account type expression: {}", expression, e);
      return null;
    }
  }

  /** Create SpEL Assessment Context */
  private EvaluationContext createEvaluationContext(
      ProceedingJoinPoint joinPoint, Method method, HttpServletRequest request) {
    EvaluationContext context = new StandardEvaluationContext();

    // Set variable
    context.setVariable("request", request);
    context.setVariable("args", joinPoint.getArgs());

    // setmethod parameter name (if available)
    String[] paramNames = getParameterNames(method);
    if (paramNames != null) {
      Object[] args = joinPoint.getArgs();
      for (int i = 0; i < paramNames.length && i < args.length; i++) {
        context.setVariable(paramNames[i], args[i]);
      }
    }

    return context;
  }

  /** getmethod parameter name */
  private String[] getParameterNames(Method method) {
    try {
      // Use reflectionget parameter name (preserve the parameter name at compile time, or use another method)
      // Use DefaultParameterNameDiscoverer to get parameter names
      org.springframework.core.DefaultParameterNameDiscoverer discoverer =
          new org.springframework.core.DefaultParameterNameDiscoverer();
      return discoverer.getParameterNames(method);
    } catch (Exception e) {
      log.debug("Failed to get method parameter names: {}");
      return null;
    }
  }

  @Override
  public boolean requiresSecondAuth(HttpServletRequest request, String service) {
    // Only the expression method is supported, this method no longer checkrequest parameter
    return false;
  }

  private boolean isSafeExpression(String expression) {
    if (!StringUtils.hasText(expression)) {
      return false;
    }
    String exp = expression.replaceAll("\\s+", "");
    if (exp.contains("(") || exp.contains(")") || exp.contains("T(") || exp.contains("'")) {
      return false;
    }
    Pattern p =
        Pattern.compile(
            "^#(args\\[\\d+\\]|[a-zA-Z_][a-zA-Z0-9_]*)(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");
    return p.matcher(exp).matches();
  }

  private Object evaluateSimpleExpression(
      String expression,
      ProceedingJoinPoint joinPoint,
      Method method,
      HttpServletRequest request) {
    String exp = expression.replaceAll("\\s+", "");
    if (!isSafeExpression(exp)) {
      return null;
    }
    String root;
    String[] parts;
    if (exp.startsWith("#args[")) {
      int i = exp.indexOf(']');
      String idxStr = exp.substring(6, i);
      int idx = Integer.parseInt(idxStr);
      root = "args[" + idxStr + "]";
      String rest = exp.substring(i + 1);
      parts = rest.startsWith(".") ? rest.substring(1).split("\\.") : new String[0];
      Object[] args = joinPoint.getArgs();
      Object value = idx >= 0 && idx < args.length ? args[idx] : null;
      for (String part : parts) {
        if (value == null) {
          return null;
        }
        value = readProperty(value, part);
      }
      return value;
    } else {
      int dot = exp.indexOf('.');
      if (dot >= 0) {
        root = exp.substring(1, dot);
        parts = exp.substring(dot + 1).split("\\.");
      } else {
        root = exp.substring(1);
        parts = new String[0];
      }
      Object value = getVariableValue(root, joinPoint, method, request);
      for (String part : parts) {
        if (value == null) {
          return null;
        }
        value = readProperty(value, part);
      }
      return value;
    }
  }

  private Object getVariableValue(
      String name, ProceedingJoinPoint joinPoint, Method method, HttpServletRequest request) {
    if ("request".equals(name)) {
      return request;
    }
    Object[] args = joinPoint.getArgs();
    String[] paramNames = getParameterNames(method);
    if (paramNames != null) {
      for (int i = 0; i < paramNames.length && i < args.length; i++) {
        if (name.equals(paramNames[i])) {
          return args[i];
        }
      }
    }
    // Fallback: single body arg (AOP method may lack -parameters names)
    if (args != null && args.length == 1 && args[0] != null && !"request".equals(name)) {
      log.debug(
          "Param name '{}' not found on {}; falling back to args[0] ({})",
          name,
          method.getName(),
          args[0].getClass().getSimpleName());
      return args[0];
    }
    return null;
  }

  private Object readProperty(Object bean, String prop) {
    try {
      PropertyDescriptor pd = new PropertyDescriptor(prop, bean.getClass());
      Method read = pd.getReadMethod();
      if (read == null) {
        return null;
      }
      return read.invoke(bean);
    } catch (Exception e) {
      // Fallback: direct getter / field for records without standard Introspector metadata
      try {
        String getter =
            "get" + Character.toUpperCase(prop.charAt(0)) + (prop.length() > 1 ? prop.substring(1) : "");
        Method m = bean.getClass().getMethod(getter);
        return m.invoke(bean);
      } catch (Exception ignored) {
        log.debug("readProperty failed bean={} prop={}", bean.getClass().getName(), prop);
        return null;
      }
    }
  }

  @Override
  public boolean requiresSecondAuth(
      HttpServletRequest request,
      String service,
      ProceedingJoinPoint joinPoint,
      Method method,
      RequireSecondAuth annotation) {
    // Only the expression method is supported, the amount expression is required
    if (annotation != null && StringUtils.hasText(annotation.amountExpression())) {
      return true;
    }
    return false;
  }
}
