package org.jeecg.common.system.annotation;

import java.lang.annotation.*;

/**
 * Convert enumeration class into dictionary data
 * @Author taoYan
 * @Date 2022/7/8 10:34
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnumDict {

    /**
     * Unique code for dictionary data.
     */
    String value() default "";
}
