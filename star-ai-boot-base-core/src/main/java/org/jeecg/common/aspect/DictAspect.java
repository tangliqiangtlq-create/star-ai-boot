package org.jeecg.common.aspect;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.common.aspect.annotation.FilePreview;
import org.jeecg.common.system.vo.DictModel;
import org.jeecg.common.util.FilePreviewUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.common.valid.DictLookupFacade;
import org.jeecg.config.JeecgBaseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@Order(1)
public class DictAspect {

  @Lazy @Autowired private ObjectMapper objectMapper;

  @Autowired private CommonAPI commonAPI;

  /**
   * Resolves table/column dictionary reads through the per-service chain (local schema → registered
   * peer-service provider → {@code CommonAPI}). See {@link DictLookupFacade}.
   */
  @Autowired private DictLookupFacade dictLookupFacade;

  @Resource private JeecgBaseConfig jeecgBaseConfig;

  @Pointcut(
      "execution(public * org.jeecg.modules..*.*Controller.*(..)) || @annotation(org.jeecg.common.aspect.annotation.AutoDict) || @annotation(org.jeecg.common.aspect.annotation.FilePreview)")
  public void excudeService() {}

  @Around("excudeService()")
  public Object doAround(ProceedingJoinPoint pjp) throws Throwable {

    Object result = pjp.proceed();

    // Absolute preview base URL: prefer request Origin/Referer (e.g. http://127.0.0.1:3100/api/sys/common/file)
    String baseURL = FilePreviewUtils.resolveFilePreviewBaseUrl();
    if (StrUtil.isBlank(baseURL)) {
      String apiSub = normalizeApiSub(jeecgBaseConfig.getApiSubdomainAddress());
      if (StrUtil.isBlank(apiSub)) {
        apiSub = "/api";
      }
      String pc =
          jeecgBaseConfig.getDomainUrl() != null ? jeecgBaseConfig.getDomainUrl().getPc() : null;
      if (StrUtil.isNotBlank(pc)) {
        while (pc.endsWith("/")) {
          pc = pc.substring(0, pc.length() - 1);
        }
        baseURL = pc + apiSub + "/sys/common/file";
      } else {
        log.warn("DictAspect skip FilePreview: cannot resolve frontend baseURL");
        return result;
      }
    }

    StopWatch stopWatch = new StopWatch();
    stopWatch.start("DictAspect");

    try {
      result = processAnnotations(result, baseURL);
    } catch (Exception e) {
      log.warn("DictAspect process failed, return raw result: {}", e);
    }

    stopWatch.stop();

    log.info(stopWatch.prettyPrint(TimeUnit.MILLISECONDS));

    return result;
  }

  private Object processAnnotations(Object result, String baseURL) {

    if (result instanceof Result) {

      Result resultObj = (Result) result;

      Object data = resultObj.getResult();

      if (data == null) {
        return result;
      }

      if (data instanceof List) {

        List updateData = processAnnotationsForRecords((List<?>) data, baseURL);

        resultObj.setResult(updateData);

      } else if (data instanceof IPage) {
        IPage<?> page = (IPage<?>) data;

        List updateData = processAnnotationsForRecords(page.getRecords(), baseURL);

        page.setRecords(updateData);
      } else {
        List updateData = processAnnotationsForRecords(Collections.singletonList(data), baseURL);
        if (!updateData.isEmpty()) {
          resultObj.setResult(updateData.get(0));
        }
      }
    }
    return result;
  }

  private static String normalizeApiSub(String apiSub) {
    if (StrUtil.isBlank(apiSub)) {
      return "";
    }
    String normalized = apiSub.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private List processAnnotationsForRecords(List<?> records, String baseURL) {

    if (records.isEmpty()) {
      return records;
    }

    Object obj = records.get(0);

    if (obj instanceof Map) {
      return records;
    }

    Class<?> clazz = obj.getClass();
    Package objPackage = clazz.getPackage();

    // Empty package or java.* usually means not a business entity
    if (objPackage == null || objPackage.getName().startsWith("java.")) {
      return records;
    }

    List<Map<String, Object>> updatedRecords = new ArrayList<>();

    Field[] allFields = oConvertUtils.getAllFields(obj);

    Map<String, FormattedDictPrefetch> formattedDictPrefetchMap =
        buildFormattedDictPrefetchMap(allFields, records);

    // B10-1: batch-fetch (a) non-formatted common-dict (@Dict with NO dictTable, i.e. sys_dict table)
    // and (b) non-formatted table-dict (@Dict with dictTable but NO dicTextFormat). Replaces N
    // per-row translateDict() / translateDictFromTable() calls with one call per group.
    Map<String, Map<String, String>> commonDictBatch =
        buildCommonDictBatchMap(allFields, recordMapsFromList(records, allFields));
    Map<TableDictGroupKey, Map<String, String>> tableDictBatch =
        buildTableDictBatchMap(allFields, recordMapsFromList(records, allFields));

    for (Object record : records) {

      if (!record.getClass().getName().equals(obj.getClass().getName())) {

        log.info(
            "Type 1:{}, Type 2:{} different types of data; skip dictionary and file authorization injection",
            record.getClass().getName(),
            obj.getClass().getName());

        return records;
      }

      // Convert entity to Map; skip conversion if already a Map
      Map<String, Object> recordMap = convertObjectToMap(record);

      if (recordMap == null) {

        log.info("Failed to convert object to Map, skipping dictionary and file authorization injection");

        return records;
      }

      // Process dict and file-preview fields
      for (Field field : allFields) {
        String fieldName = field.getName();
        Object fieldValue = recordMap.get(fieldName);

        Dict dictAnnotation = field.getAnnotation(Dict.class);
        FilePreview filePreview = field.getAnnotation(FilePreview.class);

        // Handle @Dict
        if (dictAnnotation != null) {
          processDictAnnotation(
              field,
              dictAnnotation,
              recordMap,
              fieldName,
              fieldValue,
              formattedDictPrefetchMap.get(fieldName),
              commonDictBatch,
              tableDictBatch);
        }

        // Handle @FilePreview
        if (filePreview != null) {
          processFilePreviewAnnotation(
              field, recordMap, fieldName, fieldValue, baseURL, filePreview.isRichText());
        }
      }

      // Updated recordMap replaces the original record
      updatedRecords.add(recordMap);
    }

    return updatedRecords;
  }

  // ======= helpers: convert record list to list<map> (reuse existing convertObjectToMap) =======
  private List<Map<String, Object>> recordMapsFromList(List<?> records, Field[] ignored) {
    if (records == null || records.isEmpty()) return Collections.emptyList();
    List<Map<String, Object>> out = new ArrayList<>(records.size());
    for (Object r : records) {
      Map<String, Object> m = convertObjectToMap(r);
      if (m != null) out.add(m);
    }
    return out;
  }

  /**
   * Group non-formatted, common-dict fields (@Dict.dictTable blank) by {@code dicCode} →
   * translateManyDict(dictCodesCsv, keysCsv) once. Returns: dicCode → key → text.
   */
  private Map<String, Map<String, String>> buildCommonDictBatchMap(
      Field[] allFields, List<Map<String, Object>> recordMaps) {
    if (recordMaps.isEmpty()) return Collections.emptyMap();
    Map<String, Set<String>> groupKeys = new HashMap<>();
    for (Field field : allFields) {
      Dict d = field.getAnnotation(Dict.class);
      if (d == null) continue;
      if (StrUtil.isNotBlank(d.dictTable())) continue; // handled in table-dict group
      if (StrUtil.isBlank(d.dicCode())) continue;
      // already handled by FormattedDictPrefetch (dictTable) branch: skip
      if (StrUtil.isNotBlank(d.dicTextFormat())) continue;
      Set<String> s = groupKeys.computeIfAbsent(d.dicCode(), k -> new LinkedHashSet<>());
      for (Map<String, Object> m : recordMaps) {
        Object v = m.get(field.getName());
        if (v != null && StrUtil.isNotBlank(v.toString())) s.add(v.toString());
      }
    }
    if (groupKeys.isEmpty()) return Collections.emptyMap();
    Map<String, Map<String, String>> result = new HashMap<>();
    // translateManyDict accepts dictCodes as CSV and keys as CSV; returns Map<String,List<DictModel>>
    // keyed by dicCode. We need to call it per dicCode (safer: translateManyDict doesn't dedupe
    // well for multiple dicCodes with identical keys). So we iterate.
    for (Map.Entry<String, Set<String>> e : groupKeys.entrySet()) {
      String dicCode = e.getKey();
      Set<String> rawKeys = e.getValue();
      if (rawKeys == null || rawKeys.isEmpty()) continue;
      // Step K7-1: cache-hit keys go directly into result; only misses call Feign.
      Map<String, String> codeMap = new HashMap<>();
      Set<String> missKeys = new LinkedHashSet<>();
      for (String k : rawKeys) {
        if (StrUtil.isBlank(k)) continue;
        String txt = DictLocalCache.getCommon(dicCode, k);
        if (txt != null) {
          codeMap.put(k, txt);
        } else {
          missKeys.add(k);
        }
      }
      if (!missKeys.isEmpty()) {
        try {
          Map<String, List<DictModel>> r =
              commonAPI.translateManyDict(dicCode, String.join(",", missKeys));
          if (r != null && !r.isEmpty()) {
            List<DictModel> list = r.get(dicCode);
            if (list != null && !list.isEmpty()) {
              for (DictModel dm : list) {
                if (dm == null) continue;
                if (StrUtil.isBlank(dm.getValue())) continue;
                if (StrUtil.isNotBlank(dm.getText())) {
                  codeMap.put(dm.getValue(), dm.getText());
                  DictLocalCache.putCommon(dicCode, dm.getValue(), dm.getText());
                }
              }
            }
          }
        } catch (RuntimeException ex) {
          log.warn("DictAspect buildCommonDictBatchMap fail dicCode={}: {}", dicCode, ex.getMessage());
        }
      }
      if (!codeMap.isEmpty()) result.put(dicCode, codeMap);
    }
    return result;
  }

  /** Group key for table-based dict batch lookups (dictTable + dicText + dicCode + ds tuple). */
  private static final class TableDictGroupKey {
    final String table;
    final String text;
    final String code;
    final String ds;
    private final int hc;

    TableDictGroupKey(String table, String text, String code, String ds) {
      this.table = table;
      this.text = text;
      this.code = code;
      this.ds = ds;
      this.hc = Objects.hash(table, text, code, ds);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TableDictGroupKey)) return false;
      TableDictGroupKey k = (TableDictGroupKey) o;
      return hc == k.hc
          && Objects.equals(table, k.table)
          && Objects.equals(text, k.text)
          && Objects.equals(code, k.code)
          && Objects.equals(ds, k.ds);
    }

    @Override
    public int hashCode() {
      return hc;
    }
  }

  /**
   * Group non-formatted, table-dict fields (@Dict.dictTable non-blank, no dicTextFormat) by
   * TableDictGroupKey → translateDictFromTableByKeys(table,text,code,keys,ds) per group. Returns
   * group key → key → text.
   */
  private Map<TableDictGroupKey, Map<String, String>> buildTableDictBatchMap(
      Field[] allFields, List<Map<String, Object>> recordMaps) {
    if (recordMaps.isEmpty()) return Collections.emptyMap();
    Map<TableDictGroupKey, Set<String>> groupKeys = new HashMap<>();
    for (Field field : allFields) {
      Dict d = field.getAnnotation(Dict.class);
      if (d == null) continue;
      if (StrUtil.isBlank(d.dictTable())) continue;
      if (StrUtil.isBlank(d.dicCode())) continue;
      if (StrUtil.isBlank(d.dicText())) continue;
      if (StrUtil.isNotBlank(d.dicTextFormat())) continue; // handled by FormattedDictPrefetch
      TableDictGroupKey gk = new TableDictGroupKey(d.dictTable(), d.dicText(), d.dicCode(), d.ds());
      Set<String> s = groupKeys.computeIfAbsent(gk, k -> new LinkedHashSet<>());
      for (Map<String, Object> m : recordMaps) {
        Object v = m.get(field.getName());
        if (v != null && StrUtil.isNotBlank(v.toString())) s.add(v.toString());
      }
    }
    if (groupKeys.isEmpty()) return Collections.emptyMap();
    Map<TableDictGroupKey, Map<String, String>> result = new HashMap<>();
    for (Map.Entry<TableDictGroupKey, Set<String>> e : groupKeys.entrySet()) {
      TableDictGroupKey gk = e.getKey();
      Set<String> rawKeys = e.getValue();
      if (rawKeys == null || rawKeys.isEmpty()) continue;
      // Step K7-2: table-dict cache-hit keys go directly into result; miss keys call Feign once.
      Map<String, String> codeMap = new HashMap<>();
      Set<String> missKeys = new LinkedHashSet<>();
      for (String k : rawKeys) {
        if (StrUtil.isBlank(k)) continue;
        String txt = DictLocalCache.getTable(gk.table, gk.text, gk.code, gk.ds, k);
        if (txt != null) {
          codeMap.put(k, txt);
        } else {
          missKeys.add(k);
        }
      }
      if (!missKeys.isEmpty()) {
        try {
          Map<String, String> fetched =
              dictLookupFacade.translateBatch(gk.table, gk.text, gk.code, missKeys, gk.ds);
          for (Map.Entry<String, String> entry : fetched.entrySet()) {
            if (StrUtil.isBlank(entry.getKey()) || StrUtil.isBlank(entry.getValue())) {
              continue;
            }
            codeMap.put(entry.getKey(), entry.getValue());
            DictLocalCache.putTable(
                gk.table, gk.text, gk.code, gk.ds, entry.getKey(), entry.getValue());
          }
        } catch (RuntimeException ex) {
          log.warn(
              "DictAspect buildTableDictBatchMap fail table={} text={} code={}: {}",
              gk.table,
              gk.text,
              gk.code,
              ex.getMessage());
        }
      }
      if (!codeMap.isEmpty()) result.put(gk, codeMap);
    }
    return result;
  }

  private Map<String, Object> convertObjectToMap(Object record) {
    if (record instanceof Map) {
      return (Map<String, Object>) record; // Already a Map
    } else {
      try {
        // Convert object to Map
        return objectMapper.convertValue(record, Map.class);
      } catch (Exception e) {
        log.error("Failed to convert object to Map", e);
        return null;
      }
    }
  }

  private void processDictAnnotation(
      Field field,
      Dict dictAnnotation,
      Map<String, Object> recordMap,
      String fieldName,
      Object fieldValue,
      FormattedDictPrefetch formattedDictPrefetch,
      Map<String, Map<String, String>> commonDictBatch,
      Map<TableDictGroupKey, Map<String, String>> tableDictBatch) {

    if (fieldValue == null) {
      return;
    }

    String dicCode = dictAnnotation.dicCode();
    String dicText = dictAnnotation.dicText();
    String dictTable = dictAnnotation.dictTable();

    String dictText;

    if (formattedDictPrefetch != null) {
      dictText = formattedDictPrefetch.format(fieldValue.toString());
    } else if (StrUtil.isBlank(dictTable)) {
      // common sys_dict table: look up in batch map first, fallback to single
      Map<String, String> cache = commonDictBatch == null ? null : commonDictBatch.get(dicCode);
      if (cache != null) {
        dictText = cache.get(fieldValue.toString());
      } else {
        dictText = commonAPI.translateDict(dicCode, fieldValue.toString());
      }
    } else {
      // Table-based dict lookup: batch first, fallback single
      TableDictGroupKey gk = new TableDictGroupKey(dictTable, dicText, dicCode, dictAnnotation.ds());
      Map<String, String> cache = tableDictBatch == null ? null : tableDictBatch.get(gk);
      if (cache != null) {
        dictText = cache.get(fieldValue.toString());
      } else {
        dictText =
            dictLookupFacade.translate(
                dictTable, dicText, dicCode, fieldValue.toString(), dictAnnotation.ds());
      }
    }

    // Store item_text as-is (may be multilingual JSON {"zh_CN":"...","en":"..."}); frontend parses by lang
    recordMap.put(fieldName + "_dictText", dictText);
  }

  private void processFilePreviewAnnotation(
      Field field,
      Map<String, Object> recordMap,
      String fieldName,
      Object fieldValue,
      String baseURL,
      boolean isRichText) {

    if (null == fieldValue) {
      return;
    }

    // Convert file ID to preview URL
    Object convertedValue = FilePreviewUtils.convertFileIdToUrl(fieldValue, baseURL, isRichText);
    recordMap.put(fieldName, convertedValue);
  }

  private Map<String, FormattedDictPrefetch> buildFormattedDictPrefetchMap(
      Field[] allFields, List<?> records) {
    if (records == null || records.isEmpty()) {
      return Collections.emptyMap();
    }

    List<Map<String, Object>> recordMaps = new ArrayList<>(records.size());
    for (Object record : records) {
      Map<String, Object> recordMap = convertObjectToMap(record);
      if (recordMap == null) {
        return Collections.emptyMap();
      }
      recordMaps.add(recordMap);
    }

    Map<String, FormattedDictPrefetch> result = new HashMap<>();
    for (Field field : allFields) {
      Dict dict = field.getAnnotation(Dict.class);
      if (dict == null) {
        continue;
      }
      if (StrUtil.isBlank(dict.dictTable()) || StrUtil.isBlank(dict.dicCode())) {
        continue;
      }
      if (StrUtil.isBlank(dict.dicTextFormat()) || StrUtil.isBlank(dict.dicText())) {
        continue;
      }

      String[] textFields = StrUtil.splitToArray(dict.dicText(), ',');
      if (textFields == null || textFields.length == 0) {
        continue;
      }

      String fieldName = field.getName();
      Set<String> keys = new LinkedHashSet<>();
      for (Map<String, Object> recordMap : recordMaps) {
        Object keyObj = recordMap.get(fieldName);
        if (keyObj != null && StrUtil.isNotBlank(keyObj.toString())) {
          keys.add(keyObj.toString());
        }
      }
      if (keys.isEmpty()) {
        continue;
      }

      List<Map<String, String>> valueToTextList = new ArrayList<>(textFields.length);
      for (String textField : textFields) {
        String normalizedTextField = StrUtil.trim(textField);
        Map<String, String> valueToTextMap =
            dictLookupFacade.translateBatch(
                dict.dictTable(), normalizedTextField, dict.dicCode(), keys, dict.ds());
        valueToTextList.add(valueToTextMap == null ? new HashMap<>() : valueToTextMap);
      }

      result.put(
          fieldName,
          new FormattedDictPrefetch(textFields, valueToTextList, dict.dicTextFormat()));
    }
    return result;
  }

  private static class FormattedDictPrefetch {
    private final String[] textFields;
    private final List<Map<String, String>> valueToTextList;
    private final String template;

    private FormattedDictPrefetch(
        String[] textFields, List<Map<String, String>> valueToTextList, String template) {
      this.textFields = textFields;
      this.valueToTextList = valueToTextList;
      this.template = template;
    }

    private String format(String key) {
      String[] parts = new String[textFields.length];
      for (int i = 0; i < textFields.length; i++) {
        String part = null;
        if (i < valueToTextList.size()) {
          part = valueToTextList.get(i).get(key);
        }
        parts[i] = part == null ? "" : part;
      }

      String formatted = template;
      for (int i = 0; i < parts.length; i++) {
        formatted = formatted.replace("{" + i + "}", parts[i]);
      }
      return formatted;
    }
  }
}
