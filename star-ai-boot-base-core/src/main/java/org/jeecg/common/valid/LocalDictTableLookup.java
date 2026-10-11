package org.jeecg.common.valid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jeecg.common.util.SqlInjectionUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves dictionary tables against the current service's own data source, reusing the same
 * sanitizing rules as the system side ({@link SqlInjectionUtil}).
 *
 * <p>Table/column names are always compile-time literals or come from a service-owned allowlist, so
 * the sanitize step is a defence-in-depth check rather than the primary control.
 */
@Component
public class LocalDictTableLookup implements DictTableLookup {

  private final JdbcTemplate jdbcTemplate;

  /** table name → exists in the current schema (cached; schema does not change at runtime). */
  private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();

  public LocalDictTableLookup(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean supports(String table) {
    if (table == null || table.trim().isEmpty()) {
      return false;
    }
    return tableExistsCache.computeIfAbsent(table.trim(), this::tableExistsInCurrentSchema);
  }

  private boolean tableExistsInCurrentSchema(String table) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.TABLES where TABLE_SCHEMA = DATABASE() and TABLE_NAME = ?",
            Long.class,
            table);
    return count != null && count > 0;
  }

  @Override
  public String translate(String table, String text, String code, String key) {
    SqlInjectionUtil.filterContentMulti(table, text, code, key);
    String t = SqlInjectionUtil.getSqlInjectTableName(table);
    String f = SqlInjectionUtil.getSqlInjectField(text);
    String c = SqlInjectionUtil.getSqlInjectField(code);

    List<String> result =
        jdbcTemplate.query(
            "select " + f + " from " + t + " where " + c + " = ? limit 1",
            (rs, rowNum) -> rs.getString(1),
            key);
    return result.isEmpty() ? null : result.get(0);
  }

  @Override
  public Map<String, String> translateBatch(
      String table, String text, String code, Collection<String> keys) {
    Map<String, String> out = new LinkedHashMap<>();
    if (keys == null || keys.isEmpty()) {
      return out;
    }
    // Multi-column text (comma separated) cannot be aliased into a single result column; degrade.
    if (text != null && text.indexOf(',') >= 0) {
      return DictTableLookup.super.translateBatch(table, text, code, keys);
    }
    List<String> safeKeys =
        keys.stream()
            .filter(k -> k != null && !k.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    if (safeKeys.isEmpty()) {
      return out;
    }
    SqlInjectionUtil.filterContentMulti(table, text, code);
    String t = SqlInjectionUtil.getSqlInjectTableName(table);
    String f = SqlInjectionUtil.getSqlInjectField(text);
    String c = SqlInjectionUtil.getSqlInjectField(code);
    String placeholders = safeKeys.stream().map(k -> "?").collect(Collectors.joining(","));

    jdbcTemplate.query(
        "select " + c + " as k, " + f + " as v from " + t + " where " + c + " in (" + placeholders + ")",
        rs -> {
          String key = rs.getString("k");
          String value = rs.getString("v");
          if (key != null && value != null) {
            out.put(key, value);
          }
        },
        safeKeys.toArray());
    return out;
  }
}
