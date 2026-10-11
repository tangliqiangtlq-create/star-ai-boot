package org.jeecg.common.util;

import java.util.Locale;
import org.springframework.context.MessageSource;

/**
 * Multilingual tool class used to simplify the acquisition of multilingual messages
 *
 * @author jeecg-boot
 */
public class I18nUtil {

  /**
   * Get MessageSource
   *
   * @return MessageSource
   */
  private static MessageSource getMessageSource() {
    return SpringContextUtils.getBean(MessageSource.class);
  }

  /**
   * Get message for the current locale
   *
   * @param key message key
   * @return message content
   */
  public static String getMessage(String key) {
    return getMessage(key, (Object[]) null);
  }

  /**
   * Get message for the current locale
   *
   * @param key message key
   * @param args Message arguments
   * @return message content
   */
  public static String getMessage(String key, Object[] args) {
    Locale locale = LocaleResolver.getCurrLocale();
    return getMessageSource().getMessage(key, args, key, locale);
  }

  /**
   * Get message for the specified locale
   *
   * @param key message key
   * @param locale Locale
   * @return message content
   */
  public static String getMessage(String key, Locale locale) {
    return getMessage(key, (Object[]) null, locale);
  }

  /**
   * Get message for the specified locale
   *
   * @param key message key
   * @param args Message arguments
   * @param locale Locale
   * @return message content
   */
  public static String getMessage(String key, Object[] args, Locale locale) {
    return getMessageSource().getMessage(key, args, key, locale);
  }
}
