package org.jeecg.common.aspect.annotation;

import java.lang.annotation.*;

/**
 * annotation for onlinerequest blocking
 * @author: jeecg-boot
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
@Documented
public @interface OnlineAuth {

    /**
     * request keyword, string before xxx/code
     * @return
     */
    String value();
}
