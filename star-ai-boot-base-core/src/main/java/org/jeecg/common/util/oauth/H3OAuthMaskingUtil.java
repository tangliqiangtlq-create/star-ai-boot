package org.jeecg.common.util.oauth;

public class H3OAuthMaskingUtil {

  public static final int DEFAULT_HEAD = 3;
  public static final int DEFAULT_TAIL = 3;
  public static final int DEFAULT_MAX_TOTAL_EXPOSED = 8;

  private H3OAuthMaskingUtil() {
  }

  public static String safeMaskHeadTail(String value) {
    return safeMaskHeadTail(value, DEFAULT_HEAD, DEFAULT_TAIL, DEFAULT_MAX_TOTAL_EXPOSED);
  }

  public static String safeMaskHeadTail(String value, int head, int tail, int maxTotalExposed) {
    if (value == null) {
      return null;
    }
    int len = value.length();
    if (len == 0) {
      return "";
    }
    if (head < 0) {
      head = 0;
    }
    if (tail < 0) {
      tail = 0;
    }
    if (maxTotalExposed < 0) {
      maxTotalExposed = 0;
    }
    int totalExposed = head + tail;
    if (len <= totalExposed || len <= maxTotalExposed) {
      if (len <= 4) {
        return repeat("*", len);
      }
      totalExposed = Math.min(totalExposed, maxTotalExposed);
      head = Math.min(head, totalExposed / 2);
      tail = totalExposed - head;
    }
    if (totalExposed > maxTotalExposed) {
      int half = maxTotalExposed / 2;
      head = Math.min(head, half);
      tail = maxTotalExposed - head;
    }
    if (head + tail >= len) {
      return repeat("*", Math.min(4, len));
    }
    String headPart = value.substring(0, head);
    String tailPart = value.substring(len - tail);
    return headPart + "***" + tailPart;
  }

  private static String repeat(String s, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  public static String safeMaskJsonField(String jsonLike, String fieldName) {
    if (jsonLike == null || fieldName == null) {
      return jsonLike;
    }
    java.util.regex.Pattern p =
        java.util.regex.Pattern.compile(
            "(?i)([\"']?" + java.util.regex.Pattern.quote(fieldName) + "[\"']?\\s*[:=]\\s*)([\"']?)([^&\\s,;\"'\\)]{4,})([\"']?)");
    return p.matcher(jsonLike).replaceAll("$1$2***$4");
  }

  public static String safeMaskQueryParam(String url, String... paramNames) {
    if (url == null || paramNames == null || paramNames.length == 0) {
      return url;
    }
    if (!url.contains("?")) {
      return url;
    }
    java.util.Set<String> nameSet = new java.util.HashSet<>();
    for (String p : paramNames) {
      if (p != null) nameSet.add(p.toLowerCase(java.util.Locale.ROOT));
    }
    int q = url.indexOf('?');
    String base = url.substring(0, q);
    String query = url.substring(q + 1);
    String[] parts = query.split("&");
    java.util.List<String> out = new java.util.ArrayList<>(parts.length);
    for (String part : parts) {
      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      if (eq < 0) { out.add(part); continue; }
      String k = part.substring(0, eq);
      String v = part.substring(eq + 1);
      if (nameSet.contains(k.toLowerCase(java.util.Locale.ROOT))) {
        v = safeMaskHeadTail(v);
      }
      out.add(k + "=" + v);
    }
    return base + "?" + String.join("&", out);
  }
}
