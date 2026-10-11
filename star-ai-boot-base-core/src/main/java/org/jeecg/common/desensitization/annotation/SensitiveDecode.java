package org.jeecg.common.desensitization.annotation;

import java.lang.annotation.*;

/**
 * Decrypt annotation
 *
 * Define on the method to decrypt the sensitive Field in the object returned by the method. It should be noted that if it has not been encrypted, there will be problems with the decryption and the original string will be returned.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface SensitiveDecode {

    /**
     * Entity class that requires desensitization
     * @return
     */
    Class entity() default Object.class;
}
