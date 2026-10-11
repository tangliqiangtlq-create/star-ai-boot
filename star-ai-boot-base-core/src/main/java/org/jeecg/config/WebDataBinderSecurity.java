package org.jeecg.config;

import org.springframework.web.bind.WebDataBinder;

/**
 * Spring MVC data binding security: defend against Mass Assignment / insecure binders
 * (e.g. CVE-2022-22965 related chains).
 * <p>
 * Blacklist restricts class manipulation chains and common sensitive fields; for stronger constraints,
 * use {@link WebDataBinder#setAllowedFields(String...)} whitelist on specific Controllers.
 */
public final class WebDataBinderSecurity {

  private WebDataBinderSecurity() {}

  /**
   * Default forbidden request parameter paths (aligned with Spring official class property manipulation protection, extended for multipart and password fields).
   */
  public static final String[] DEFAULT_DISALLOWED_FIELDS =
      new String[] {
        "class.*",
        "Class.*",
        "class.module.*",
        "Class.module.*",
        "*.class.*",
        "*.Class.*",
        "*.class.module.*",
        "*.Class.module.*",
        "classLoader.*",
        "ClassLoader.*",
        "*.classLoader.*",
        "*.ClassLoader.*",
        "multipartFiles",
        "password"
      };

  public static void applyMassAssignmentProtection(WebDataBinder binder) {
    if (binder != null) {
      binder.setDisallowedFields(DEFAULT_DISALLOWED_FIELDS);
    }
  }
}
