package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Resolve display metadata from {@code oss_file} by objectId / preview URL.
 * Shared by business detail APIs so callers do not re-implement oss lookups.
 */
@Slf4j
public final class OssFileDisplayMetaUtil {

  private OssFileDisplayMetaUtil() {}

  /**
   * @param fileRef preview URL, raw objectId, or comma-separated (first wins)
   * @return map keys: objectId, originalFileName, fileName, size, ext; or null when not found
   */
  public static Map<String, Object> lookup(String fileRef) {
    String objectId = safeObjectId(fileRef);
    if (StrUtil.isBlank(objectId)) {
      return null;
    }
    try {
      JdbcTemplate jdbcTemplate = SpringContextUtils.getBean(JdbcTemplate.class);
      List<Map<String, Object>> rows =
          jdbcTemplate.queryForList(
              "SELECT object_id, original_file_name, file_name, size, ext FROM oss_file"
                  + " WHERE object_id = ? OR object_id = ? LIMIT 1",
              objectId,
              "/" + objectId);
      if (rows == null || rows.isEmpty()) {
        return null;
      }
      Map<String, Object> row = rows.get(0);
      Map<String, Object> meta = new HashMap<>(8);
      meta.put("objectId", StrUtil.blankToDefault(asString(row.get("object_id")), objectId));
      String original = asString(row.get("original_file_name"));
      String storedName = asString(row.get("file_name"));
      meta.put("originalFileName", original);
      meta.put("fileName", StrUtil.blankToDefault(original, storedName));
      meta.put("size", row.get("size"));
      meta.put("ext", asString(row.get("ext")));
      return meta;
    } catch (Exception e) {
      log.warn("Lookup oss_file display meta failed for objectId={}: {}", objectId, e.toString());
      return null;
    }
  }

  /** Prefer original display name; fallback to stored file_name. */
  public static String resolveDisplayName(String fileRef) {
    Map<String, Object> meta = lookup(fileRef);
    if (meta == null) {
      return null;
    }
    return asString(meta.get("fileName"));
  }

  private static String safeObjectId(String fileRef) {
    if (StrUtil.isBlank(fileRef)) {
      return null;
    }
    try {
      String first = StrUtil.trim(fileRef.split(",")[0]);
      return CommonUtils.convertObjectId(first);
    } catch (Exception e) {
      return null;
    }
  }

  private static String asString(Object value) {
    return value == null ? null : StrUtil.trimToNull(String.valueOf(value));
  }
}
