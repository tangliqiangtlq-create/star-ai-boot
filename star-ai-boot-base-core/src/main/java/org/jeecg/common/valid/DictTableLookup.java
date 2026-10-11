package org.jeecg.common.valid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SPI for dictionary table lookups. The default implementation resolves table/column names inside
 * the current service's own data source (see {@link LocalDictTableLookup}).
 */
public interface DictTableLookup {

  /**
   * Translate one key into the text column value.
   *
   * @param table dictionary table name
   * @param text column holding the returned text
   * @param code column holding the key
   * @param key the key to look up
   * @return the text value, or {@code null} when the key does not exist
   */
  String translate(String table, String text, String code, String key);

  /**
   * Whether this lookup is able to resolve {@code table} without leaving the current service.
   *
   * <p>Callers (e.g. {@code DictAspect} / {@code DictLookupFacade}) use this to decide between the
   * local data source and the cross-service {@code CommonAPI} path. Defaults to {@code false} so an
   * implementation that only supports {@link #translate} keeps the legacy behaviour.
   */
  default boolean supports(String table) {
    return false;
  }

  /**
   * Batch translate several keys of the same table in one round trip.
   *
   * <p>The default implementation degrades to repeated {@link #translate} calls; local
   * implementations should override it with a single {@code IN (...)} query.
   *
   * @return key → text, only present when the text is non-blank
   */
  default Map<String, String> translateBatch(
      String table, String text, String code, Collection<String> keys) {
    Map<String, String> out = new LinkedHashMap<>();
    if (keys == null || keys.isEmpty()) {
      return out;
    }
    for (String key : keys) {
      if (key == null || key.isEmpty()) {
        continue;
      }
      String value = translate(table, text, code, key);
      if (value != null) {
        out.put(key, value);
      }
    }
    return out;
  }
}
