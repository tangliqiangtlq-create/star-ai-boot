package org.jeecg.common.desensitization.annotation;


import org.jeecg.common.desensitization.enums.SensitiveEnum;

import java.lang.annotation.*;

/**
 * Mark a field as storing sensitive information
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SensitiveField {

    /**
     * Handled differently by type.
     * @return
     */
    SensitiveEnum type() default SensitiveEnum.ENCODE;
}
