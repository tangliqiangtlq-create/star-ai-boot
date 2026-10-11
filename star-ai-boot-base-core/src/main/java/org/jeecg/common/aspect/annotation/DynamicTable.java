package org.jeecg.common.aspect.annotation;

import java.lang.annotation.*;

/**
 * Dynamic table switching
 *
 * @author :zyf
 * @date:2020-04-25
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DynamicTable {
    /**
     * Table name that needs to be dynamically resolved
     * @return
     */
    String value();
}
