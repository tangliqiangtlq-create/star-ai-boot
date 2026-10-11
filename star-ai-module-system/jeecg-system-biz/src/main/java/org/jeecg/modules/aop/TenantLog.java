package org.jeecg.modules.aop;

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
public @interface TenantLog {

	/**
	 * Operation log type (1 query, 2 add, 3 update, 4 delete)
	 * 
	 * @return
	 */
	int value() default 0;

}
