package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import java.util.Locale;

/**
 * Dictionary entry multilingual text parsing tool.
 *
 * <p>The dictionary item {@code item_text} may be an ordinary string or in JSON format, for example:
 * {@code {"zh_CN":"Enable","en":"Active"}} or {@code {"zh_CN":"Enable","en_US":"Active"}}.
 * This tool parses the display text in the corresponding language according to the request header {@code lang}, and is compatible with {@code en}/{@code en_US}, {@code zh_CN}/{@code zh-CN}.
 */
public final class DictI18nUtil {

  private static final String[] EN_KEYS = {"en", "en_US", "en-US"};
  private static final String[] ZH_KEYS = {"zh_CN", "zh-CN", "zh"};

  private DictI18nUtil() {}

  public static String resolveLocalizedText(String rawText) {
    return resolveLocalizedText(rawText, LocaleResolver.getCurrLang());
  }

  public static String resolveLocalizedText(String rawText, String lang) {
    if (StrUtil.isBlank(rawText)) {
      return rawText;
    }

    String trimmed = rawText.trim();
    if (!trimmed.startsWith("{")) {
      return rawText;
    }

    try {
      JSONObject json = JSONObject.parseObject(trimmed);
      if (json == null || json.isEmpty()) {
        return rawText;
      }
      return resolveFromJson(json, lang);
    } catch (Exception ignored) {
      return rawText;
    }
  }

  private static String resolveFromJson(JSONObject json, String lang) {
    String currentLang = StrUtil.isBlank(lang) ? "zh_CN" : lang.trim();

    String matched = firstNonBlank(json, langKeys(currentLang));
    if (StrUtil.isNotBlank(matched)) {
      return matched;
    }

    String fallbackLang = isEnglish(currentLang) ? "zh_CN" : "en";
    matched = firstNonBlank(json, langKeys(fallbackLang));
    if (StrUtil.isNotBlank(matched)) {
      return matched;
    }

    for (String key : json.keySet()) {
      String value = json.getString(key);
      if (StrUtil.isNotBlank(value)) {
        return value;
      }
    }

    return json.toJSONString();
  }

  private static String firstNonBlank(JSONObject json, String[] keys) {
    for (String key : keys) {
      if (!json.containsKey(key)) {
        continue;
      }
      String text = json.getString(key);
      if (StrUtil.isNotBlank(text)) {
        return text;
      }
    }
    return null;
  }

  private static boolean isEnglish(String lang) {
    String normalized = lang.toLowerCase(Locale.ROOT).replace('-', '_');
    return "en".equals(normalized) || normalized.startsWith("en_");
  }

  private static String[] langKeys(String lang) {
    return isEnglish(lang) ? EN_KEYS : ZH_KEYS;
  }
}
