package org.jeecg.common.aspect.annotation;

import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.enums.ModuleType;

import java.lang.annotation.*;

/**
 * System log annotation
 * 
 * @Author scott
 * @email jeecgos@163.com
 * @Date January 14, 2019
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoLog {

	/**
	 * Log content
	 * 
	 * @return
	 */
	String value() default "";

	/**
	 * Log type
	 * 
	 * @return 0: operation log; 1: login log; 2: scheduled job;
	 */
	int logType() default CommonConstant.LOG_TYPE_2;
	
	/**
	 * Operation log type
	 * 
	 * @return (1 query, 2 add, 3 update, 4 delete)
	 */
	int operateType() default 0;

	/**
	 * Module type; default is common
	 * @return
	 */
	ModuleType module() default ModuleType.COMMON;
}
