package org.jeecg.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use uniformly Jackson conduct JSON Deserialization, avoid Fastjson Typical usage of libraries on untrusted data (with Spring MVC default JSON stack consistent).
 */
public final class JeecgJsonUtils {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    MAPPER.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
  }

  private JeecgJsonUtils() {}

  public static ObjectMapper getMapper() {
    return MAPPER;
  }

  /**
   * Will JSON The object resolves to {@code Map<String,String>}，The value is uniformly converted to a string (the same as the original Fastjson + downstream {@code String#valueOf} usage compatible).
   */
  public static Map<String, String> parseToStringMap(String json) throws IOException {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    Map<String, Object> raw =
        MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    Map<String, String> result = new LinkedHashMap<>(raw.size());
    for (Map.Entry<String, Object> e : raw.entrySet()) {
      Object v = e.getValue();
      result.put(e.getKey(), v == null ? null : String.valueOf(v));
    }
    return result;
  }

  public static Map<String, Object> parseToMap(String json) throws IOException {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  public static <T> List<T> parseArray(String json, Class<T> elementClass) throws IOException {
    CollectionType ct = MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass);
    return MAPPER.readValue(json, ct);
  }

  public static <T> T parseObject(String json, Class<T> clazz) throws IOException {
    return MAPPER.readValue(json, clazz);
  }
}
