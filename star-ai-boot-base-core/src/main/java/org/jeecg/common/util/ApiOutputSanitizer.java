package org.jeecg.common.util;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * API response body output sanitization: eliminate executable HTML/script fragments before untrusted data enters {@link org.jeecg.common.api.vo.Result#getResult()},
 * Use {@code X-Content-Type-Options: nosniff} to reduce cross-site scripting and MIME sniffing risks (SECZ-SSCSP-CODE-003/021).
 */
public final class ApiOutputSanitizer {

  private static final int MAX_DEPTH = 24;
  private static final PolicyFactory XSS_POLICY = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

  /**
   * Real HTML markup only (not comparisons like {@code a < b} / {@code score > 0}).
   *
   * <ul>
   *   <li>{@code <!-- ... -->}
   *   <li>{@code <tag>}, {@code </tag>}, {@code <tag/>}, {@code <tag attr="...">}
   * </ul>
   *
   * Tag name must start with a letter; a trailing {@code >} is required so {@code a<b} does not match.
   */
  private static final Pattern HTML_TAG =
      Pattern.compile(
          "(?is)<!--.*?-->|</?[a-z][a-z0-9]*(?:\\s[^>]*)?/?>");

  private ApiOutputSanitizer() {}

  @SuppressWarnings("unchecked")
  public static <T> T sanitizeForOutput(T value) {
    IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
    return (T) sanitizeValue(value, 0, visited);
  }

  private static Object sanitizeValue(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
    if (value == null || depth > MAX_DEPTH) {
      return value;
    }
    if (isSimpleValue(value)) {
      return value;
    }
    if (value instanceof String str) {
      return sanitizeString(str);
    }
    if (visited.containsKey(value)) {
      return value;
    }
    visited.put(value, Boolean.TRUE);

    if (value instanceof JSONObject jsonObject) {
      JSONObject copy = new JSONObject(jsonObject.size(), false);
      jsonObject.forEach((k, v) -> copy.set(k, sanitizeValue(v, depth + 1, visited)));
      return copy;
    }
    if (value instanceof JSONArray jsonArray) {
      JSONArray copy = new JSONArray(jsonArray.size());
      for (int i = 0; i < jsonArray.size(); i++) {
        copy.add(sanitizeValue(jsonArray.get(i), depth + 1, visited));
      }
      return copy;
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> copy = new LinkedHashMap<>(map.size());
      map.forEach((k, v) -> copy.put(k, sanitizeValue(v, depth + 1, visited)));
      return copy;
    }
    if (value instanceof IPage<?> page) {
      List<?> records = page.getRecords();
      if (records != null) {
        List<Object> sanitizedRecords = new ArrayList<>(records.size());
        for (Object record : records) {
          sanitizedRecords.add(sanitizeValue(record, depth + 1, visited));
        }
        if (records instanceof ArrayList) {
          records.clear();
          ((List<Object>) records).addAll(sanitizedRecords);
        } else {
          page.setRecords((List) sanitizedRecords);
        }
      }
      return value;
    }
    if (value instanceof Collection<?> collection) {
      // JSONArray has been processed above; other collections (including ArrayList) maintain List semantics to avoid destroying business strong Type
      List<Object> list = new ArrayList<>(collection.size());
      for (Object item : collection) {
        list.add(sanitizeValue(item, depth + 1, visited));
      }
      return list;
    }
    if (value.getClass().isArray()) {
      int len = Array.getLength(value);
      Object[] copy = new Object[len];
      for (int i = 0; i < len; i++) {
        copy[i] = sanitizeValue(Array.get(value, i), depth + 1, visited);
      }
      return copy;
    }
    sanitizePojoFields(value, depth, visited);
    return value;
  }

  private static void sanitizePojoFields(
      Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
    for (Field field : ReflectUtil.getFields(value.getClass())) {
      if (field.getType().isPrimitive() || !ReflectionAccessUtil.isAllowedReflectField(field)) {
        continue;
      }
      Object fieldValue = ReflectionAccessUtil.getFieldValue(field, value);
      if (fieldValue == null && field.getType().isPrimitive()) {
        continue;
      }
      Object sanitized = sanitizeValue(fieldValue, depth + 1, visited);
      if (sanitized != fieldValue) {
        try {
          ReflectionAccessUtil.setFieldValue(field, value, sanitized);
        } catch (IllegalAccessException ignored) {
          // Unwritable Field skip
        }
      }
    }
  }

  private static String sanitizeString(String value) {
    if (value.isEmpty()) {
      return value;
    }
    // Fields such as multilingual menus store JSON text (such as meta.title); HTML purification of the entire paragraph will turn " into &#34;, causing the front end to be unable to parse
    if (JSONUtil.isTypeJSONObject(value)) {
      try {
        return sanitizeJsonObject(JSONUtil.parseObj(value)).toString();
      } catch (Exception ignore) {
        // Fallback: value looked like JSON but failed to parse (e.g. nested JSON-in-string); treat as plain text
      }
    }
    if (JSONUtil.isTypeJSONArray(value)) {
      try {
        return sanitizeJsonArray(JSONUtil.parseArray(value)).toString();
      } catch (Exception ignore) {
        // Fallback: same as above
      }
    }
    // Only purify when real HTML tags are present (emails / "a < b" stay untouched)
    if (!containsHtmlTag(value)) {
      return value;
    }
    return restoreEmailAt(XSS_POLICY.sanitize(value));
  }

  static boolean containsHtmlTag(String value) {
    return value != null && value.indexOf('<') >= 0 && HTML_TAG.matcher(value).find();
  }

  /** OWASP Sanitizers.LINKS encodes '@' → &#64;; restore for JSON API consumers. */
  private static String restoreEmailAt(String value) {
    if (value == null || value.isEmpty() || value.indexOf('&') < 0) {
      return value;
    }
    return value.replace("&#64;", "@").replace("&#x40;", "@").replace("&#X40;", "@");
  }

  /** Only the string leaf nodes in JSON are purified, keeping the JSON structure unchanged (consistent with the input parameter {@link ContentValidator} strategy). */
  private static JSONObject sanitizeJsonObject(JSONObject obj) {
    JSONObject copy = new JSONObject(obj.size(), false);
    obj.forEach((key, val) -> copy.set(key, sanitizeJsonElement(val)));
    return copy;
  }

  private static JSONArray sanitizeJsonArray(JSONArray arr) {
    JSONArray copy = new JSONArray(arr.size());
    for (int i = 0; i < arr.size(); i++) {
      copy.add(sanitizeJsonElement(arr.get(i)));
    }
    return copy;
  }

  private static Object sanitizeJsonElement(Object val) {
    if (val instanceof String str) {
      return sanitizeString(str);
    }
    if (val instanceof JSONObject jo) {
      return sanitizeJsonObject(jo);
    }
    if (val instanceof JSONArray ja) {
      return sanitizeJsonArray(ja);
    }
    return val;
  }

  /** Feign / After purification, it may be {@link LinkedHashMap} and uniformly converted to hutool {@link JSONObject} for business use. */
  public static JSONObject asJSONObject(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof JSONObject jsonObject) {
      return jsonObject;
    }
    if (value instanceof Map<?, ?> map) {
      return new JSONObject(map);
    }
    return JSONUtil.parseObj(value);
  }

  private static boolean isSimpleValue(Object value) {
    return value instanceof Number
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof Enum<?>
        || value instanceof Date
        || value instanceof Temporal
        || value instanceof UUID
        || value instanceof BigDecimal
        || value instanceof BigInteger
        || value.getClass().isPrimitive();
  }
}
