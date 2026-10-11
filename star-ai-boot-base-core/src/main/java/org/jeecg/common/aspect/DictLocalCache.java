package org.jeecg.common.aspect;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import cn.hutool.core.util.StrUtil;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DictLocalCache {

  private static final Cache<String, String> TEXT_CACHE =
      CacheBuilder.newBuilder()
          .maximumSize(100_000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build();

  private static String commonKey(String dicCode, String key) {
    return "C:" + dicCode + ":" + key;
  }

  private static String tableKey(String table, String text, String code, String ds, String key) {
    String d = StrUtil.isBlank(ds) ? "-" : ds;
    return "T:" + table + "|" + text + "|" + code + "|" + d + ":" + key;
  }

  public static String getCommon(String dicCode, String key) {
    if (StrUtil.isBlank(dicCode) || StrUtil.isBlank(key)) {
      return null;
    }
    return TEXT_CACHE.getIfPresent(commonKey(dicCode, key));
  }

  public static void putCommon(String dicCode, String key, String text) {
    if (StrUtil.isBlank(dicCode) || StrUtil.isBlank(key) || StrUtil.isBlank(text)) {
      return;
    }
    TEXT_CACHE.put(commonKey(dicCode, key), text);
  }

  public static String getTable(String table, String textCol, String codeCol, String ds, String key) {
    if (StrUtil.isBlank(table) || StrUtil.isBlank(codeCol) || StrUtil.isBlank(key)) {
      return null;
    }
    return TEXT_CACHE.getIfPresent(tableKey(table, textCol, codeCol, ds, key));
  }

  public static void putTable(
      String table, String textCol, String codeCol, String ds, String key, String text) {
    if (StrUtil.isBlank(table) || StrUtil.isBlank(codeCol) || StrUtil.isBlank(key) || StrUtil.isBlank(text)) {
      return;
    }
    TEXT_CACHE.put(tableKey(table, textCol, codeCol, ds, key), text);
  }

  public static void bustAll() {
    TEXT_CACHE.invalidateAll();
  }

  @EventListener(ContextRefreshedEvent.class)
  public void onContextRefreshed() {
    bustAll();
  }
}
