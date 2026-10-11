package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.vo.DomainUrl;
import org.springframework.core.env.Environment;

/**
 * File preview tool class
 * used to fileIDConvert to accessible fileURL
 *
 * @author jeecg-boot
 */
@Slf4j
public class FilePreviewUtils {

  private static final String FILE_PREVIEW_PATH = "/sys/common/file";
  private static final Pattern OBJECT_ID_PARAM =
      Pattern.compile("(?i)(?:\\?|&|^)objectId=([^&#]+)");
  private static final Pattern UPLOAD_OBJECT_ID_IN_TEXT =
      Pattern.compile("(upload/\\d{8}/[^\\s\"'<>]+)");
  /** Broken objectId without slash after date: upload/20260922file_xxx.jpeg */
  private static final Pattern UPLOAD_OBJECT_ID_MISSING_SLASH =
      Pattern.compile("(upload/\\d{8})(?!/)([^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);

  /**
   * fileIDConvert to fileURL(Automatically retrieved from requestbaseURL)
   * Suitable for annotation scenarios, it will automatically obtain the gateway path and configuration from the current request.
   *
   * @param fieldValue File ID value; supports String (comma-separated), JSONArray, ArrayList
   * @param isRichText Whether rich-text mode
   * @return Converted file URL or original value
   */
  public static Object convertFileIdToUrl(Object fieldValue, boolean isRichText) {
    String baseURL = getBaseUrlFromRequest();
    if (StrUtil.isBlank(baseURL)) {
      log.warn("Unable to get baseURL, returning original value");
      return fieldValue;
    }
    return convertFileIdToUrl(fieldValue, baseURL, isRichText);
  }

  /**
   * fileIDConvert to fileURL(Use the specifiedbaseURL)
   * Applicable tocodingCalling scenes can be customizedbaseURL
   *
   * @param fieldValue File ID value; supports String (comma-separated), JSONArray, ArrayList
   * @param baseURL Basics of file accessURL
   * @param isRichText Whether rich-text mode
   * @return Converted file URL or original value
   */
  public static Object convertFileIdToUrl(Object fieldValue, String baseURL, boolean isRichText) {
    if (fieldValue == null) {
      return null;
    }

    if (StrUtil.isBlank(baseURL)) {
      log.warn("baseURL is empty, returning original value");
      return fieldValue;
    }

    // Rich text mode processing
    if (isRichText) {
      return processRichText(fieldValue, baseURL);
    }

    // Ordinary documentIDConvert
    return processFileIds(fieldValue, baseURL);
  }

  /**
   * Handling rich text mode
   *
   * @param fieldValue Field value
   * @param baseURL base URL
   * @return processed text
   */
  private static Object processRichText(Object fieldValue, String baseURL) {
    try {
      Environment environment = SpringContextUtils.getBean(Environment.class);
      String[] origins =
          environment.getProperty("spring.web.cors.allowed-origins", String[].class);

      if (origins == null || origins.length == 0) {
        log.warn("Allowed source address not configured, returning original value");
        return fieldValue;
      }

      String text = fieldValue.toString();

      if (StrUtil.isBlank(text)) {
        return fieldValue;
      }

      String targetOrigin = extractOrigin(baseURL);
      if (StrUtil.isBlank(targetOrigin)) {
        return fieldValue;
      }

      for (String allowedOrigin : origins) {
        if (StrUtil.isNotBlank(allowedOrigin) && !allowedOrigin.equals(targetOrigin)) {
          text = text.replace(allowedOrigin, targetOrigin);
        }
      }

      return text;
    } catch (Exception e) {
      log.error("Failed to process rich text", e);
      return fieldValue;
    }
  }

  /**
   * Process filesIDConvert
   *
   * @param fieldValue Field value
   * @param baseURL base URL
   * @return convertedURL
   */
  private static Object processFileIds(Object fieldValue, String baseURL) {
    if (fieldValue instanceof String) {
      return processStringFileIds((String) fieldValue, baseURL);
    } else if (fieldValue instanceof JSONArray) {
      return processJsonArrayFileIds((JSONArray) fieldValue, baseURL);
    } else if (fieldValue instanceof ArrayList) {
      return processArrayListFileIds((ArrayList<String>) fieldValue, baseURL);
    } else {
      log.error("Unsupported file preview field type: {}", fieldValue.getClass().getName());
      return fieldValue;
    }
  }

  /**
   * deal withStringTypefilesID(comma separated)
   *
   * @param objectId documentIDString, multiple separated by commas
   * @param baseURL base URL
   * @return convertedURLString, multiple separated by commas
   */
  private static String processStringFileIds(String objectId, String baseURL) {
    if (StrUtil.isBlank(objectId)) {
      return objectId;
    }

    String[] objectIds = objectId.split(",");
    for (int i = 0; i < objectIds.length; i++) {
      String normalized = extractObjectIdForPreview(objectIds[i]);
      if (StrUtil.isBlank(normalized)) {
        objectIds[i] = objectIds[i] != null ? objectIds[i].trim() : null;
        continue;
      }
      objectIds[i] = buildPreviewUrl(baseURL, normalized);
    }

    return StrUtil.join(",", (Object[]) objectIds);
  }

  /**
   * deal withJSONArrayTypefilesID
   *
   * @param jsonArray JSONArrayobject
   * @param baseURL base URL
   * @return convertedJSONArray
   */
  private static JSONArray processJsonArrayFileIds(JSONArray jsonArray, String baseURL) {
    for (int i = 0; i < jsonArray.size(); i++) {
      Object item = jsonArray.get(i);
      if (item != null) {
        String normalized = extractObjectIdForPreview(item.toString());
        if (StrUtil.isBlank(normalized)) {
          jsonArray.set(i, item.toString().trim());
        } else {
          jsonArray.set(i, buildPreviewUrl(baseURL, normalized));
        }
      }
    }
    return jsonArray;
  }

  /**
   * deal withArrayListTypefilesID
   *
   * @param arrayList ArrayListobject
   * @param baseURL base URL
   * @return convertedArrayList
   */
  private static ArrayList<String> processArrayListFileIds(
      ArrayList<String> arrayList, String baseURL) {
    for (int i = 0; i < arrayList.size(); i++) {
      String item = arrayList.get(i);
      if (StrUtil.isNotBlank(item)) {
        String normalized = extractObjectIdForPreview(item);
        if (StrUtil.isBlank(normalized)) {
          arrayList.set(i, item.trim());
        } else {
          arrayList.set(i, buildPreviewUrl(baseURL, normalized));
        }
      }
    }
    return arrayList;
  }

  /**
   * The front-end public network root reachable by the current request (such as {@code http://localhost:3100}), Excluding trailing slash, excluding /api. 
   * <p>Parsing order:Origin/Referer/Reverse proxy header → {@code jeecg.domainUrl.pc} → The gateway has all the details. Returns an empty string on failure.
   */
  public static String resolvePublicFrontendBase() {
    try {
      JeecgBaseConfig jeecgBaseConfig = SpringContextUtils.getBean(JeecgBaseConfig.class);
      HttpServletRequest request = RequestUtils.getCurrentRequest();
      if (request != null) {
        String base = resolvePublicBase(request, jeecgBaseConfig);
        return base != null ? base : "";
      }
      DomainUrl domainUrl = jeecgBaseConfig != null ? jeecgBaseConfig.getDomainUrl() : null;
      String configuredPc = domainUrl != null ? domainUrl.getPc() : null;
      String normalized = normalizeBase(configuredPc);
      return normalized != null ? normalized : "";
    } catch (Exception e) {
      log.debug("Failed to get frontend public root", e);
      return "";
    }
  }

  /**
   * Reachable by current request API Public network root (such as {@code http://localhost:3100/api}), No trailing slash is included.
   * <p>exist {@link #resolvePublicFrontendBase()} splicing based on {@code jeecg.api-subdomain-address}. 
   * Returns an empty string on failure.
   */
  public static String resolveApiPublicRoot() {
    try {
      JeecgBaseConfig jeecgBaseConfig = SpringContextUtils.getBean(JeecgBaseConfig.class);
      String base = resolvePublicFrontendBase();
      String apiSub =
          normalizeApiSub(jeecgBaseConfig != null ? jeecgBaseConfig.getApiSubdomainAddress() : null);
      if (StrUtil.isBlank(base) && StrUtil.isBlank(apiSub)) {
        return "";
      }
      return base + apiSub;
    } catch (Exception e) {
      log.debug("Failed to get API public root", e);
      return "";
    }
  }

  /**
   * Get file preview from current request base(Contains {@code /sys/common/file}). 
   *
   * <p>Prioritize requests Origin/Referer Fight absolutelyAddress(like {@code http://127.0.0.1:3100/api/sys/common/file}), 
   * Ensure that it is consistent with the domain name currently accessed by the browser.Cookie Portable.
   */
  private static String getBaseUrlFromRequest() {
    try {
      String apiRoot = resolveApiPublicRoot();
      if (StrUtil.isBlank(apiRoot)) {
        log.warn("Unable to get baseURL, returning original value");
        return null;
      }
      return apiRoot + FILE_PREVIEW_PATH;
    } catch (Exception e) {
      log.error("GetbaseURLFailed", e);
      return null;
    }
  }

  /**
   * for DictAspect etcComponentReuse: File preview under current request is absolute base(Contains /sys/common/file). 
   */
  public static String resolveFilePreviewBaseUrl() {
    return getBaseUrlFromRequest();
  }

  private static String resolvePublicBase(HttpServletRequest request, JeecgBaseConfig jeecgBaseConfig) {
    String origin = request.getHeader("origin");
    String normalizedOrigin = normalizeBase(origin);
    if (normalizedOrigin != null) {
      return normalizedOrigin;
    }

    String referer = request.getHeader("referer");
    String refererOrigin = extractOrigin(referer);
    if (refererOrigin != null) {
      return refererOrigin;
    }

    String forwardedBase = extractForwardedBase(request);
    if (forwardedBase != null) {
      return forwardedBase;
    }

    String xForwardedBase = extractXForwardedBase(request);
    if (xForwardedBase != null) {
      return xForwardedBase;
    }

    DomainUrl domainUrl = jeecgBaseConfig != null ? jeecgBaseConfig.getDomainUrl() : null;
    String configuredPc = domainUrl != null ? domainUrl.getPc() : null;
    String normalizedConfigured = normalizeBase(configuredPc);
    if (normalizedConfigured != null) {
      return normalizedConfigured;
    }

    String gatewayBase = request.getHeader("x_gateway_base_path");
    String normalizedGateway = normalizeBase(gatewayBase);
    if (normalizedGateway != null) {
      return normalizedGateway;
    }

    String fallback = normalizeBase(CommonUtils.getBaseUrl(request));
    return fallback != null ? fallback : "";
  }

  private static String normalizeApiSub(String apiSub) {
    if (StrUtil.isBlank(apiSub)) {
      return "";
    }
    String normalized = apiSub.trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String normalizeBase(String input) {
    if (StrUtil.isBlank(input)) {
      return null;
    }
    String trimmed = input.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String extractOrigin(String url) {
    if (StrUtil.isBlank(url)) {
      return null;
    }
    try {
      URI uri = URI.create(url.trim());
      if (StrUtil.isBlank(uri.getScheme()) || StrUtil.isBlank(uri.getAuthority())) {
        return null;
      }
      return normalizeBase(uri.getScheme() + "://" + uri.getAuthority());
    } catch (Exception e) {
      return null;
    }
  }

  private static String extractForwardedBase(HttpServletRequest request) {
    String forwarded = request.getHeader("forwarded");
    if (StrUtil.isBlank(forwarded)) {
      return null;
    }
    String first = forwarded.split(",", 2)[0];
    String proto = null;
    String host = null;
    String[] parts = first.split(";");
    for (String part : parts) {
      String p = part.trim();
      if (p.startsWith("proto=")) {
        proto = stripQuotes(p.substring("proto=".length()).trim());
      } else if (p.startsWith("host=")) {
        host = stripQuotes(p.substring("host=".length()).trim());
      }
    }
    if (StrUtil.isBlank(host)) {
      return null;
    }
    String scheme = StrUtil.isBlank(proto) ? request.getScheme() : proto;
    return normalizeBase(scheme + "://" + host);
  }

  private static String extractXForwardedBase(HttpServletRequest request) {
    String proto = request.getHeader("x-forwarded-proto");
    if (StrUtil.isBlank(proto)) {
      proto = request.getHeader("x-forwarded-scheme");
    }
    String host = request.getHeader("x-forwarded-host");
    String port = request.getHeader("x-forwarded-port");

    if (StrUtil.isBlank(host)) {
      return null;
    }

    String selectedProto = StrUtil.isBlank(proto) ? request.getScheme() : proto;
    String selectedHost = host.split(",", 2)[0].trim();
    String selectedPort = StrUtil.isBlank(port) ? null : port.split(",", 2)[0].trim();

    if (StrUtil.isBlank(selectedPort) || selectedHost.contains(":")) {
      return normalizeBase(selectedProto + "://" + selectedHost);
    }
    if (("http".equalsIgnoreCase(selectedProto) && "80".equals(selectedPort))
        || ("https".equalsIgnoreCase(selectedProto) && "443".equals(selectedPort))) {
      return normalizeBase(selectedProto + "://" + selectedHost);
    }
    return normalizeBase(selectedProto + "://" + selectedHost + ":" + selectedPort);
  }

  private static String stripQuotes(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim();
    if (v.length() >= 2
        && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
      return v.substring(1, v.length() - 1);
    }
    return v;
  }

  private static String extractObjectIdForPreview(String input) {
    if (StrUtil.isBlank(input)) {
      return null;
    }
    String current = input.trim();
    for (int i = 0; i < 5; i++) {
      Matcher matcher = OBJECT_ID_PARAM.matcher(current);
      if (!matcher.find()) {
        break;
      }
      current = urlDecodeUtf8(matcher.group(1)).trim();
    }
    Matcher uploadMatcher = UPLOAD_OBJECT_ID_IN_TEXT.matcher(current);
    if (uploadMatcher.find()) {
      return uploadMatcher.group(1);
    }
    Matcher broken = UPLOAD_OBJECT_ID_MISSING_SLASH.matcher(current);
    if (broken.find()) {
      return broken.group(1) + "/" + broken.group(2);
    }
    while (current.startsWith("/")) {
      current = current.substring(1);
    }
    // Never wrap API error text / i18n messages (e.g. login timeout) into /file?objectId=
    if (current.startsWith("http://")
        || current.startsWith("https://")
        || !UPLOAD_OBJECT_ID_IN_TEXT.matcher(current).find()) {
      // one more try after normalize
      Matcher again = UPLOAD_OBJECT_ID_MISSING_SLASH.matcher(current);
      if (again.find()) {
        return again.group(1) + "/" + again.group(2);
      }
      return null;
    }
    return current;
  }

  private static String buildPreviewUrl(String baseURL, String normalized) {
    return baseURL + "?objectId=" + encodeObjectId(normalized);
  }

  private static String encodeObjectId(String objectId) {
    if (StrUtil.isBlank(objectId)) {
      return objectId;
    }
    StringBuilder encoded = new StringBuilder(objectId.length() + 16);
    for (int i = 0; i < objectId.length(); i++) {
      char ch = objectId.charAt(i);
      if (isObjectIdSafeChar(ch)) {
        encoded.append(ch);
      } else {
        encoded.append(URLEncoder.encode(String.valueOf(ch), StandardCharsets.UTF_8));
      }
    }
    return encoded.toString();
  }

  private static boolean isObjectIdSafeChar(char ch) {
    return (ch >= 'a' && ch <= 'z')
        || (ch >= 'A' && ch <= 'Z')
        || (ch >= '0' && ch <= '9')
        || ch == '/'
        || ch == '-'
        || ch == '_'
        || ch == '.';
  }

  private static String urlDecodeUtf8(String value) {
    if (value == null) {
      return null;
    }
    try {
      return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
    } catch (Exception e) {
      return value;
    }
  }

  /**
   * Build file previewURL
   * Convenience method to build a preview URL for a single file ID
   *
   * @param fileId File ID
   * @param baseURL base URL
   * @return Full file preview URL
   */
  public static String buildFileUrl(String fileId, String baseURL) {
    if (StrUtil.isBlank(fileId) || StrUtil.isBlank(baseURL)) {
      return fileId;
    }
    String normalized = extractObjectIdForPreview(fileId);
    if (StrUtil.isBlank(normalized)) {
      return fileId;
    }
    return buildPreviewUrl(baseURL, normalized);
  }

  /**
   * Build file previewURL(Automatically retrieved from requestbaseURL)
   * Convenience method to build a preview URL for a single file ID
   *
   * @param fileId File ID
   * @return Full file preview URL
   */
  public static String buildFileUrl(String fileId) {
    String baseURL = getBaseUrlFromRequest();
    if (StrUtil.isBlank(baseURL)) {
      log.warn("Unable to get baseURL, returning original file ID");
      return fileId;
    }
    return buildFileUrl(fileId, baseURL);
  }

  /**
   * Convert files in batchesIDThe list isURLlist
   *
   * @param fileIds File ID list
   * @param baseURL base URL
   * @return URL list
   */
  public static List<String> convertFileIdsToUrls(List<String> fileIds, String baseURL) {
    if (fileIds == null || fileIds.isEmpty()) {
      return fileIds;
    }

    List<String> urls = new ArrayList<>();
    for (String fileId : fileIds) {
      if (StrUtil.isNotBlank(fileId)) {
        urls.add(buildFileUrl(fileId, baseURL));
      }
    }
    return urls;
  }

  /**
   * Convert files in batchesIDThe list isURLList (obtained automatically from requestbaseURL)
   *
   * @param fileIds File ID list
   * @return URL list
   */
  public static List<String> convertFileIdsToUrls(List<String> fileIds) {
    String baseURL = getBaseUrlFromRequest();
    if (StrUtil.isBlank(baseURL)) {
      log.warn("Unable to get baseURL, returning original file ID list");
      return fileIds;
    }
    return convertFileIdsToUrls(fileIds, baseURL);
  }
}

