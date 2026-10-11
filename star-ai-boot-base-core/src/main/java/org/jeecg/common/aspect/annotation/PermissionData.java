package org.jeecg.common.aspect.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
  *  data permissionannotation
 * @Author taoyan
 * @Date April 11, 2019
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
@Documented
public @interface PermissionData {
	/**
	 * Not used for now
	 * @return
	 */
	String value() default "";
	
	
	/**
	 * Configure menu component path for data permission
	 */
	String pageComponent() default "";
}