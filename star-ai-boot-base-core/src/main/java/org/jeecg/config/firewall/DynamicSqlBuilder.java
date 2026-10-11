package org.jeecg.config.firewall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DynamicSqlBuilder {

  private final List<String> whereClauses;
  private final List<Object> parameters;

  public DynamicSqlBuilder() {
    this.whereClauses = new ArrayList<>();
    this.parameters = new ArrayList<>();
  }

  public void pushEq(String tableName, String column, Object value) {
    if (!E2TableAllowlist.isAllowed(tableName)) {
      throw new IllegalArgumentException("E2 table not allowlisted: " + tableName);
    }
    if (column == null || !isValidIdentifier(column)) {
      throw new IllegalArgumentException("E2 invalid column identifier: " + column);
    }
    whereClauses.add(column + " = ?");
    parameters.add(value);
  }

  public void pushIn(String tableName, String column, List<?> values) {
    if (!E2TableAllowlist.isAllowed(tableName)) {
      throw new IllegalArgumentException("E2 table not allowlisted: " + tableName);
    }
    if (column == null || !isValidIdentifier(column)) {
      throw new IllegalArgumentException("E2 invalid column identifier: " + column);
    }
    if (values == null || values.isEmpty()) {
      whereClauses.add(column + " IS NULL AND " + column + " IS NOT NULL");
      return;
    }
    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        placeholders.append(", ");
      }
      placeholders.append("?");
      parameters.add(values.get(i));
    }
    whereClauses.add(column + " IN (" + placeholders + ")");
  }

  public void pushLike(String tableName, String column, String value) {
    if (!E2TableAllowlist.isAllowed(tableName)) {
      throw new IllegalArgumentException("E2 table not allowlisted: " + tableName);
    }
    if (column == null || !isValidIdentifier(column)) {
      throw new IllegalArgumentException("E2 invalid column identifier: " + column);
    }
    whereClauses.add(column + " LIKE ?");
    parameters.add(value);
  }

  public void pushTripleAllowlisted(String tableName, String columnA, Object valA,
                                    String columnB, Object valB,
                                    String columnC, Object valC) {
    if (valA != null) {
      pushEq(tableName, columnA, valA);
    }
    if (valB != null) {
      pushEq(tableName, columnB, valB);
    }
    if (valC != null) {
      pushEq(tableName, columnC, valC);
    }
  }

  public String buildWhereAnd() {
    if (whereClauses.isEmpty()) {
      return "";
    }
    return " WHERE " + String.join(" AND ", whereClauses);
  }

  public List<Object> getParameters() {
    return Collections.unmodifiableList(parameters);
  }

  public boolean hasConditions() {
    return !whereClauses.isEmpty();
  }

  public void applyFromTripleMap(String tableName, Map<String, Object> tripleMap) {
    if (tripleMap == null || tripleMap.isEmpty()) {
      return;
    }
    int count = 0;
    for (Map.Entry<String, Object> e : tripleMap.entrySet()) {
      if (count >= 3) {
        break;
      }
      pushEq(tableName, e.getKey(), e.getValue());
      count++;
    }
  }

  private static boolean isValidIdentifier(String s) {
    if (s == null || s.isEmpty()) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = (c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '_';
      if (!ok) {
        return false;
      }
    }
    return true;
  }
}
