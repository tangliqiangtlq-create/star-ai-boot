package org.jeecg.common.util;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class ETagResponseSupport {

  public static final String HEADER_IF_NONE_MATCH = "If-None-Match";
  public static final String HEADER_ETAG = "ETag";
  public static final String HEADER_CACHE_CONTROL = "Cache-Control";
  public static final String DIRECTIVE_NO_TRANSFORM = "no-transform, must-revalidate";

  private ETagResponseSupport() {}

  public static String computeWeakEtag(Object data) {
    if (data == null) {
      return "W/\"0\"";
    }
    try {
      String json = JSON.toJSONString(data);
      String hash = SecureHashUtil.sha256Encode(json, "UTF-8");
      if (hash == null || hash.isEmpty()) {
        return "W/\"empty\"";
      }
      return "W/\"" + hash + "\"";
    } catch (Exception e) {
      log.warn("[ETAG] compute etag fallback: {}", e.getMessage());
      return "W/\"err\"";
    }
  }

  public static boolean isClientCached(HttpServletRequest request, String etag) {
    if (request == null || etag == null || etag.isEmpty()) {
      return false;
    }
    String inm = request.getHeader(HEADER_IF_NONE_MATCH);
    if (inm == null || inm.isEmpty()) {
      return false;
    }
    for (String part : inm.split(",")) {
      String trimmed = part.trim();
      if (trimmed.equals("*") || trimmed.equals(etag)) {
        return true;
      }
      String etagNoW = etag.startsWith("W/") ? etag.substring(2) : etag;
      String trimmedNoW = trimmed.startsWith("W/") ? trimmed.substring(2) : trimmed;
      if (trimmedNoW.equals(etagNoW)) {
        return true;
      }
    }
    return false;
  }

  public static boolean applyEtagOrNotModified(
      HttpServletRequest request, HttpServletResponse response, String etag) {
    if (response == null) {
      return false;
    }
    if (isClientCached(request, etag)) {
      response.setStatus(HttpStatus.NOT_MODIFIED.value());
      response.setHeader(HEADER_ETAG, etag);
      response.setHeader(HEADER_CACHE_CONTROL, DIRECTIVE_NO_TRANSFORM);
      return true;
    }
    response.setHeader(HEADER_ETAG, etag);
    response.setHeader(HEADER_CACHE_CONTROL, DIRECTIVE_NO_TRANSFORM);
    return false;
  }
}
