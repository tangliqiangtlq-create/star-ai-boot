package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public class LocaleResolver {

  public static Locale getCurrLocale() throws IllegalStateException {

    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      log.error("Cannot obtain Locale in non-web environment"); // Optional: keep log records
      return Locale.SIMPLIFIED_CHINESE; // Change: return the default Locale directly
    }

    HttpServletRequest request = attributes.getRequest();

    // Set language from the lang parameter in the request
    String language = request.getHeader("lang");

    if (StrUtil.isBlank(language)) {
      return Locale.SIMPLIFIED_CHINESE;
    }

    log.info("language: {}", language);

    switch (language) {
      case "zh_CN":
        return Locale.SIMPLIFIED_CHINESE;

      case "en":
      default:
        return Locale.ENGLISH;
    }
  }

  public static String  getCurrLang() throws IllegalStateException {

    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      log.error("Cannot obtain Locale in non-web environment"); // Optional: keep log records
      return "zh_CN"; // Change: return the default Locale directly
    }

    HttpServletRequest request = attributes.getRequest();

    // Set language from the lang parameter in the request
    String language = request.getHeader("lang");

    return language;
  }

  public static Locale getCurrLocale(HttpServletRequest request) throws IllegalStateException {

    // Set language from the lang parameter in the request
    String language = request.getHeader("lang");

    if (StrUtil.isBlank(language)) {
      return Locale.SIMPLIFIED_CHINESE;
    }

    log.info("language: {}", language);

    switch (language) {
      case "zh_CN":
        return Locale.SIMPLIFIED_CHINESE;

      case "en":
      default:
        return Locale.ENGLISH;
    }
  }
}
