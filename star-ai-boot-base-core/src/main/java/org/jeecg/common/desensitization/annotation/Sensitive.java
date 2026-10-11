package org.jeecg.common.desensitization.annotation;


import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.jeecg.common.desensitization.SensitiveSerialize;
import org.jeecg.common.desensitization.enums.SensitiveEnum;

import java.lang.annotation.*;

/**
 * Mark a field as storing sensitive information
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerialize.class)
public @interface Sensitive {

    /**
     * Handled differently by type.
     * @return
     */
    SensitiveEnum type() default SensitiveEnum.ENCODE;
}
