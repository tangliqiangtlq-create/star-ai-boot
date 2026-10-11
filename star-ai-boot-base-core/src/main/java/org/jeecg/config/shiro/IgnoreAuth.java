package org.jeecg.config.shiro;

import org.springframework.web.bind.annotation.PostMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Token-free authentication annotation
 * 
 * Auth system uses Spring MVC @PostMapping paths for login-free configuration
 * @author eightmonth
 * @date 2024/2/28 9:58
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreAuth {
}
