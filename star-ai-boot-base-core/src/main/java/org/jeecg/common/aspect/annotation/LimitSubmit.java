package org.jeecg.common.aspect.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface LimitSubmit {
  String key();

  /** Default 1s */
  int limit() default 1;

  /**
   * after the request is completed, wait if it has been waiting for true
   *
   * @return
   */
  boolean needAllWait() default true;
}
