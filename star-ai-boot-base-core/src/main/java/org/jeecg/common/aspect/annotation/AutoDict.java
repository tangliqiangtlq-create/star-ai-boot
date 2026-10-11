package org.jeecg.common.aspect.annotation;

import java.lang.annotation.*;

/**
 * Automatic dictionary translation through the interface of this annotation declaration
 * 
 * @Author scott
 * @email jeecgos@163.com
 * @Date January 5, 2022
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoDict {

	/**
	 * Not used for now
	 * @return
	 */
	String value() default "";

}
