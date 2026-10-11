package org.jeecg.common.aspect.annotation;

import org.jeecg.common.constant.CommonConstant;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface H8LoginAudit {

    String value() default "Login audit";

    int logType() default CommonConstant.LOG_TYPE_1;

    String endpoint() default "";
}
