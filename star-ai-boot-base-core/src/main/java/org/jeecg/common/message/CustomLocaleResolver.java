package org.jeecg.common.message;

import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

@Slf4j
public class CustomLocaleResolver extends CookieLocaleResolver {

  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {

    String language = request.getHeader("lang");

    log.info("language: {}", language);

    switch (language) {
      case "zh":
        super.setLocale(request, response, Locale.SIMPLIFIED_CHINESE);
        break;
      case "en":
      default:
        super.setLocale(request, response, Locale.ENGLISH);
        break;
    }
  }

  @NotNull
  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    // Set language from the lang parameter in the request
    String language = request.getHeader("lang");

    log.info("language: {}", language);

    switch (language) {
      case "zh":
        return Locale.SIMPLIFIED_CHINESE;

      case "en":
      default:
        return Locale.ENGLISH;
    }
  }
}
