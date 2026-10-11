package org.jeecg.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Global secure binder configuration
 * Prevents Spring MVC parameter binding vulnerabilities (Mass Assignment / Insecure Binder Configuration)
 *
 * <p>Applies to all {@code @Controller} / {@code @RestController}, regardless of {@code JeecgController} inheritance.
 */
@ControllerAdvice
public class GlobalInitBinderConfig {

  @InitBinder
  public void initBinder(WebDataBinder binder) {
    WebDataBinderSecurity.applyMassAssignmentProtection(binder);
  }
}
