package org.jeecg.common.desensitization.annotation;

import java.lang.annotation.*;

/**
 * encryptannotation
 *
 * Declare on the method Return sensitive Field Encrypt/format in the object returned by the method
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SensitiveEncode {

    /**
     * Entity class that requires desensitization
     * @return
     */
    Class entity() default Object.class;
}
