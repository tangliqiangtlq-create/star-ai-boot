package org.jeecg.common.aspect.annotation;

import org.jeecg.common.aspect.enumeration.SecondAuthType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * second-factor authannotation
 * Interface for marking second-factor auth required
 * 
 * @author jeecg-boot
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSecondAuth {
  
  /**
   * Authentication type
   * @return Authentication type
   */
  SecondAuthType value() default SecondAuthType.VERIFICATION_CODE_ONLY;
  
  /**
   * Service ID (for the service parameter of StpUtil.openSafe)
   * If not specified, the fully qualified name of method is used
   * @ return service identity
   */
  String service() default "";
  
  /**
   * amount expression (SpEL expression)
   * Used to getamount value from method parameter, the following expressions are supported:
   * - # amount: get parameter named amount
   * - #request.amount: getamountproperty from requestobject
   * - # args [0]: get first parameter
   * - #param.amount: getamountproperty from parameter object
   * 
   * If this expression is specified, {@code AmountBasedSecondAuthResolver} /
   * {@code DynamicSecondAuthAspect} owns the factor check; {@link #value()} is ignored
   * by {@code SecondAuthAspect} (avoid default VERIFICATION_CODE_ONLY conflicting with
   * small-amount PAYMENT_PASSWORD_ONLY).
   * 
   * @ return amount expression
   */
  String amountExpression() default "";
  
  /**
   * Account type expression (SpEL expression)
   * Used to get the account type value from the method parameter, supports the following expressions:
   * - # accountType: get parameter named accountType
   * - #request.accountType: getaccountTypeproperty from requestobject
   * - # args [0] .accountType: getaccountTypeproperty from first parameter object
   * - #param.accountType: getaccountTypeproperty from parameter object
   * 
   * If this expression is specified, AmountBasedSecondAuthResolver will use this expression to get account type
   * Rather than reading from request parameters
   * 
   * @ return account type expression
   */
  String accountTypeExpression() default "";
}

