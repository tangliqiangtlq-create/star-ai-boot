package org.jeecg.common.util.security;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;

@Slf4j
public final class C3FieldReflectionInspector {

  public static final String C3_AUDIT_TAG = "__C3_FIELD_WHITELIST_AUDIT__";

  private C3FieldReflectionInspector() {}

  public static <T> Set<String> extractSetFieldNames(LambdaUpdateWrapper<T> uw) {
    Set<String> result = new HashSet<>();
    if (uw == null) {
      return result;
    }
    try {
      Field paramField = AbstractWrapper.class.getDeclaredField("paramNameValuePairs");
      paramField.setAccessible(true);
      Object paramVal = paramField.get(uw);
      if (paramVal instanceof Map) {
        Map<?, ?> pmap = (Map<?, ?>) paramVal;
        for (Object k : pmap.keySet()) {
          if (k != null) {
            String s = String.valueOf(k);
            if (s.startsWith("MPGENVAL")) {
              continue;
            }
            result.add(s);
          }
        }
      }
    } catch (Exception e) {
      log.warn("[C3] LambdaUpdateWrapper paramNameValuePairs reflect fail: {}", e.getMessage());
    }
    try {
      Method getSqlSet = AbstractWrapper.class.getMethod("getSqlSet");
      Object sqlSetObj = getSqlSet.invoke(uw);
      if (sqlSetObj instanceof String) {
        String sqlSet = (String) sqlSetObj;
        if (sqlSet != null && !sqlSet.isEmpty()) {
          for (String part : sqlSet.split(",")) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
              String col = trimmed.substring(0, eq).trim().replace("`", "");
              result.add(col);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("[C3] LambdaUpdateWrapper getSqlSet reflect fail: {}", e.getMessage());
    }
    return Collections.unmodifiableSet(result);
  }

  public static <T> Set<String> extractSetFieldNamesFromUpdate(UpdateWrapper<T> uw) {
    Set<String> result = new HashSet<>();
    if (uw == null) {
      return result;
    }
    try {
      Field paramField = AbstractWrapper.class.getDeclaredField("paramNameValuePairs");
      paramField.setAccessible(true);
      Object paramVal = paramField.get(uw);
      if (paramVal instanceof Map) {
        Map<?, ?> pmap = (Map<?, ?>) paramVal;
        for (Object k : pmap.keySet()) {
          if (k != null) {
            String s = String.valueOf(k);
            if (s.startsWith("MPGENVAL")) {
              continue;
            }
            result.add(s);
          }
        }
      }
    } catch (Exception e) {
      log.warn("[C3] UpdateWrapper paramNameValuePairs reflect fail: {}", e.getMessage());
    }
    try {
      Method getSqlSet = AbstractWrapper.class.getMethod("getSqlSet");
      Object sqlSetObj = getSqlSet.invoke(uw);
      if (sqlSetObj instanceof String) {
        String sqlSet = (String) sqlSetObj;
        if (sqlSet != null && !sqlSet.isEmpty()) {
          for (String part : sqlSet.split(",")) {
            String trimmed = part.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
              String col = trimmed.substring(0, eq).trim().replace("`", "");
              result.add(col);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("[C3] UpdateWrapper getSqlSet reflect fail: {}", e.getMessage());
    }
    return Collections.unmodifiableSet(result);
  }

  public static void assertContainsAll(Set<String> written, Set<String> required, String tag) {
    if (written == null) {
      written = Collections.emptySet();
    }
    if (required == null || required.isEmpty()) {
      return;
    }
    if (!written.containsAll(required)) {
      Set<String> missing = new HashSet<>(required);
      missing.removeAll(written);
      throw new JeecgBootException(
          "C3 BLOCK MISSING_WRITE_FIELDS tag="
              + tag
              + " missing="
              + missing
              + " required="
              + required
              + " written="
              + written
              + ". Re-add .set(...) lines or extend REQUIRED_UPDATE_FIELDS after code review.",
          500);
    }
  }
}
