package org.jeecg.common.util;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Safety HTTP Response writing: unity Content-Type、{@code X-Content-Type-Options: nosniff} and JSON serialization,
 * Reduced reflection type XSS and MIME sniff risk(SECZ-SSCSP-CODE-003 / SECZ-SSCSP-CODE-021）。
 */
public final class HttpResponseUtils {

  /** precaution JSON hijacked prefix, with SignAuthInterceptor etc. keep consistent */
  public static final String JSON_HIJACK_PREFIX = ")]}',\n";

  private HttpResponseUtils() {}

  public static void applyJsonSecurityHeaders(HttpServletResponse response) {
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);
  }

  public static void applyPlainTextSecurityHeaders(HttpServletResponse response) {
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/plain;charset=UTF-8");
    response.setHeader("X-Content-Type-Options", "nosniff");
  }

  /** Serialize the object to JSON Write (including anti-hijacking prefix) to avoid toString() reflection unescapedContent。 */
  public static void writeJson(HttpServletResponse response, Object body) throws IOException {
    applyJsonSecurityHeaders(response);
    try (PrintWriter out = response.getWriter()) {
      out.print(JSON_HIJACK_PREFIX);
      out.print(JSON.toJSONString(body));
    }
  }

  /** Write a plain text response (e.g.WeCom (Enterprise WeChat)domain namecheckfile), the browser renders as text instead of HTML。 */
  public static void writePlainText(HttpServletResponse response, String text) throws IOException {
    applyPlainTextSecurityHeaders(response);
    try (PrintWriter out = response.getWriter()) {
      out.write(text);
    }
  }
}
