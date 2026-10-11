package org.jeecg.common.util;

/* API description */
public final class HttpHeaderUtils {

  private HttpHeaderUtils() {}

  /* API description */
  public static String stripCrlf(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    if (value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
      return value;
    }
    return value.replace("\r", "").replace("\n", "");
  }
}

