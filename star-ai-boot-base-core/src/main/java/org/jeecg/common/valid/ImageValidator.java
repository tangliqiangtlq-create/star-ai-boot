package org.jeecg.common.valid;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import java.net.URI;
import java.util.regex.Pattern;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.CommonUtils;
import org.jeecg.common.util.RoleConfigUtil;
import org.jeecg.common.util.RequestUtils;
import org.jeecg.common.valid.annotation.ValidImage;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.vo.DomainUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ImageValidator implements ConstraintValidator<ValidImage, Object> {
  private static final Logger log = LoggerFactory.getLogger(ImageValidator.class);

  private static final Pattern UPLOAD_OBJECT_ID =
      Pattern.compile("upload/\\d{8}/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

  private final JdbcTemplate jdbcTemplate;
  private boolean ignoreAdmin;
  private int size;

  @Resource private JeecgBaseConfig jeecgBaseConfig;

  @Resource private CommonAPI commonAPI;

  public ImageValidator(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void initialize(ValidImage constraintAnnotation) {
    this.size = constraintAnnotation.size();
    this.ignoreAdmin = constraintAnnotation.ignoreAdmin();
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext context) {
    if (value == null || StrUtil.isBlank(value.toString())) {
      return true; // Allow null values to pass validation
    }

    HttpServletRequest request = RequestUtils.getCurrentRequest();
    String base = resolvePublicBase(request);
    String apiSub = normalizeApiSub(jeecgBaseConfig.getApiSubdomainAddress());
    String requestDomain = base + apiSub;

    if (StrUtil.isBlank(requestDomain)) {

      log.info("ImageValidator: request domain is blank");
      return false;
    }

    LoginUser loginUser = (LoginUser) StpUtil.getSession().get("loginUser");

    if (value instanceof String) {
      String[] values = value.toString().split(",");
      if (values.length < size) {
        log.info("ImageValidator: expected size: {}, actual size: {}", size, values.length);
        return false;
      }

      for (String val : values) {
        if (!isValidImageUrl(val, requestDomain, loginUser)) {
          return false;
        }
      }
    } else if (value instanceof JSONArray) {
      JSONArray values = (JSONArray) value;
      if (values.size() < size) {
        log.info("ImageValidator: expected size: {}, actual size: {}", size, values.size());
        return false;
      }

      for (Object val : values) {
        if (!isValidImageUrl(val.toString(), requestDomain, loginUser)) {
          return false;
        }
      }
    }

    return true;
  }

  private String resolvePublicBase(HttpServletRequest request) {
    String origin = request != null ? request.getHeader("origin") : null;
    String normalizedOrigin = normalizeBase(origin);
    if (normalizedOrigin != null) {
      return normalizedOrigin;
    }

    String referer = request != null ? request.getHeader("referer") : null;
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

    String gatewayBase = request != null ? request.getHeader("x_gateway_base_path") : null;
    String normalizedGateway = normalizeBase(gatewayBase);
    if (normalizedGateway != null) {
      return normalizedGateway;
    }

    String fallback = request != null ? normalizeBase(CommonUtils.getBaseUrl(request)) : null;
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
    if (request == null) {
      return null;
    }
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
    if (request == null) {
      return null;
    }
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

  private boolean isValidImageUrl(String url, String requestDomain, LoginUser loginUser) {
    // The documented contract (docs/api-currency.md) allows the field to be a full URL *or* a bare
    // upload objectId. The admin add/edit forms and the "backfill from DB when editing" path submit
    // the stored objectId (`upload/yyyyMMdd/<name>`), which carries no scheme/host and therefore
    // never matched the domain-prefix check below. Accept it explicitly: it still points at our own
    // OSS, and the canonical check is exactly the one convertObjectId enforces before persisting.
    // Non-self-hosted URLs remain rejected by the domain check that follows.
    if (isBareUploadObjectId(url)) {
      return existsOssFile(CommonUtils.normalizeUploadObjectId(url));
    }

    // Check whether the image URL starts with the current requested domain name or file preview path
    String filePreviewPrefix = requestDomain + "/sys/common/file";
    if (!url.startsWith(requestDomain) && !url.startsWith(filePreviewPrefix)) {
      log.info(
          "ImageValidator: request domain mismatch. Expected prefix: {} or {}, Found: {}",
          requestDomain,
          filePreviewPrefix,
          url);
      return false;
    }

    // If it is an administrator user and `ignoreAdmin = true`, skip the database check
    if (!RoleConfigUtil.isAdmin(commonAPI) && ignoreAdmin) {
      return true;
    }

    String objectId;
    try {
      objectId = CommonUtils.convertObjectId(url);
    } catch (IllegalArgumentException e) {
      log.info("ImageValidator: failed to extract objectId from url: {}", url);
      return false;
    }

    return existsOssFile(objectId);
  }

  /**
   * True when the value is already the canonical OSS objectId the controller will persist
   * ({@code upload/yyyyMMdd/<name>}), i.e. a scheme/host-less reference to our own bucket.
   *
   * <p>Deliberately reuses {@link CommonUtils#isValidUploadObjectId(String)} so the validator can
   * never accept a value that {@code convertObjectId} would later reject or store differently.
   */
  static boolean isBareUploadObjectId(String value) {
    if (StrUtil.isBlank(value)) {
      return false;
    }
    return CommonUtils.isValidUploadObjectId(CommonUtils.normalizeUploadObjectId(value));
  }

  /**
   * with {@link org.jeecg.modules.oss.service.impl.OssFileServiceImpl#getOssFile} and
   * CommonController file download logic remains consistent.
   */
  private boolean existsOssFile(String objectId) {
    if (StrUtil.isBlank(objectId)) {
      return false;
    }

    String normalized = objectId.trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM oss_file WHERE object_id = ? OR object_id = ?",
            Long.class,
            normalized,
            "/" + normalized);

    if (count != null && count > 0) {
      return true;
    }

    // Files with upload/ prefix are allowed not to be stored in the library (consistent with CommonController#getFile fallback)
    if (UPLOAD_OBJECT_ID.matcher(normalized).matches()) {
      return true;
    }

    log.info("ImageValidator: oss_file not found for objectId: {}", normalized);
    return false;
  }
}
