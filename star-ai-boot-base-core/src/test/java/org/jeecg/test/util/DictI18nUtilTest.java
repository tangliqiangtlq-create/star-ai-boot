package org.jeecg.test.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jeecg.common.util.DictI18nUtil;
import org.junit.jupiter.api.Test;

class DictI18nUtilTest {

  private static final String STATUS_JSON = "{\"zh_CN\":\"Enable\",\"en\":\"Active\"}";

  @Test
  void shouldReturnChineseTextWhenLangIsZhCn() {
    assertEquals("Enable", DictI18nUtil.resolveLocalizedText(STATUS_JSON, "zh_CN"));
  }

  @Test
  void shouldReturnEnglishTextWhenLangIsEn() {
    assertEquals("Active", DictI18nUtil.resolveLocalizedText(STATUS_JSON, "en"));
  }

  @Test
  void shouldReturnRawTextWhenNotJson() {
    assertEquals("Enable", DictI18nUtil.resolveLocalizedText("Enable", "en"));
  }

  @Test
  void shouldFallbackToEnglishWhenChineseMissing() {
    assertEquals("Active", DictI18nUtil.resolveLocalizedText("{\"en\":\"Active\"}", "zh_CN"));
  }

  @Test
  void shouldResolveMenuTypeByLang() {
    String firstLevel = "{\"zh_CN\":\"First-level Menu\",\"en\":\"First-level Menu\"}";
    assertEquals("First-level menu", DictI18nUtil.resolveLocalizedText(firstLevel, "zh_CN"));
    assertEquals("First-level Menu", DictI18nUtil.resolveLocalizedText(firstLevel, "en"));
  }
}
