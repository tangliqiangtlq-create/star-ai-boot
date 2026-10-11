package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * API request input parameters XSS rough inspection: intercept obvious scripts/HTML payloads before untrusted data enters the business logic (SECZ-SSCSP-CODE-003).
 */
public final class ApiInputSanitizer {

  private static final List<String> ILLEGAL_PATTERNS =
      Arrays.asList(
          "<script>",
          "</script>",
          "<html",
          "<body",
          "<iframe",
          "javascript:",
          "vbscript:",
          "onerror\\(",
          "onload\\(",
          "onclick\\(");

  /** Character sets allowed for statistical filtering class parameters (roleCode, customerType, etc.) */
  private static final Pattern FILTER_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{0,64}$");

  private ApiInputSanitizer() {}

  public static void assertNoXssInQueryParam(String paramName, String value) {
    if (StrUtil.isBlank(value)) {
      return;
    }
    for (String pattern : ILLEGAL_PATTERNS) {
      if (value.matches("(?i).*" + pattern + ".*")) {
        throw new SecurityException("Potential XSS content detected in parameter: " + paramName);
      }
    }
  }

  /**
   * Check and normalize statistical filtering code (remain empty; non-empty must be alphanumeric/underscore/hyphen).
   */
  public static String normalizeFilterCode(String value) {
    if (StrUtil.isBlank(value)) {
      return value;
    }
    if (!FILTER_CODE_PATTERN.matcher(value).matches()) {
      throw new SecurityException("Invalid filter code parameter: " + value);
    }
    return value;
  }
}
