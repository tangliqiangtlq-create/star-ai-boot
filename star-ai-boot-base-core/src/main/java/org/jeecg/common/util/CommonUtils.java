package org.jeecg.common.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.toolkit.JdbcUtils;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import java.net.URL;
import java.net.MalformedURLException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.constant.DataBaseConstant;
import org.jeecg.common.constant.ServiceNameConstants;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.filter.SsrfFileTypeFilter;
import org.jeecg.common.util.oss.OssBootUtil;
import org.jeecgframework.poi.util.PoiPublicUtil;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Description: General tools
 *
 * @author: jeecg-boot
 */
@Slf4j
public class CommonUtils {

  /** Chinese regular rules */
  private static Pattern ZHONGWEN_PATTERN = Pattern.compile("[\u4e00-\u9fa5]");

  /** File name regular string File name supported strings: alphanumeric Chinese.-_()() Characters other than this will be deleted */
  private static String FILE_NAME_REGEX = "[^A-Za-z\\.\\(\\)\\-（）\\_0-9\\u4e00-\\u9fa5]";

  private static final Pattern PATH_PATTERN = Pattern.compile("(/upload/[^?]+)");

  public static String uploadOnlineImage(
      byte[] data, String basePath, String bizPath, String uploadType) {
    String dbPath = null;
    String fileName = "image" + Math.round(Math.random() * 100000000000L);
    fileName += "." + PoiPublicUtil.getFileExtendName(data);
    try {
      // Validate bizPath securely to prevent path traversal
      if (oConvertUtils.isNotEmpty(bizPath)) {
        bizPath = sanitizePath(bizPath);
      }
      if (CommonConstant.UPLOAD_TYPE_LOCAL.equals(uploadType)) {
        File file = new File(basePath + File.separator + bizPath + File.separator);
        if (!file.exists()) {
          boolean created = file.mkdirs();
          if (!created && !file.exists()) {
              log.warn("Failed to create directory: " + file.getAbsolutePath());
          } // Create file root directory
        }
        String savePath = file.getPath() + File.separator + fileName;
        File savefile = new File(savePath);
        FileCopyUtils.copy(data, savefile);
        dbPath = bizPath + File.separator + fileName;
      } else {
        InputStream in = new ByteArrayInputStream(data);
        String relativePath = bizPath + "/" + fileName;
        if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
          dbPath = MinioUtil.upload(in, relativePath);
        } else if (CommonConstant.UPLOAD_TYPE_OSS.equals(uploadType)) {
          dbPath = OssBootUtil.upload(in, relativePath);
        }
      }
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return dbPath;
  }

  /**
   * Normalize user-controllable path segments (such as ids) as safe segments to prevent path traversal.
   * Only the last path component is retained, and only letters, numbers, underscores, and hyphens are allowed.
   *
   * @param segment The path segment entered by the user (such as Request parameters id)
   * @return safe path segment, excluding .., /, \, etc.
   */
  public static String sanitizePathSegment(String segment) {
    if (oConvertUtils.isEmpty(segment)) {
      return "unknown";
    }
    String name = FilenameUtils.getName(segment.replace("\\", "/"));
    if (oConvertUtils.isEmpty(name)) {
      return "unknown";
    }
    String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
    return oConvertUtils.isEmpty(safe) ? "unknown" : safe;
  }

  /**
   * Normalize user- or storage-controllable file extensions to safe extensions to prevent path traversal.
   * Only take the extension part, and only letters and numbers are allowed.
   *
   * @param ext original extension (such as ossFile.getExt())
   * @return safe extension without path or special characters
   */
  public static String sanitizeFileExtension(String ext) {
    if (oConvertUtils.isEmpty(ext)) {
      return "bin";
    }
    String name = FilenameUtils.getName(ext.replace("\\", "/"));
    String extension = FilenameUtils.getExtension("dummy." + name);
    if (oConvertUtils.isEmpty(extension)) {
      return "bin";
    }
    String safe = extension.replaceAll("[^a-zA-Z0-9]", "");
    return oConvertUtils.isEmpty(safe) ? "bin" : safe.toLowerCase(Locale.ROOT);
  }

  /**
   * Securely verify and normalize paths to prevent path traversal attacks
   * Allow subdirectories, but not path traversal (../ or ..\)
   *
   * @param path The path entered by the user
   * @return normalized safe path
   * @throws JeecgBootException if path contains illegal characters
   */
  public static String sanitizePath(String path) {
    if (oConvertUtils.isEmpty(path)) {
      return "";
    }
    
    // Check the path traversal sequence (the most critical check)
    if (path.contains("..") || path.contains("../") || path.contains("..\\") 
        || path.contains("..%2F") || path.contains("..%5C") || path.contains("%2E%2E")) {
      throw new JeecgBootException("Path contains illegal characters; path traversal is forbidden");
    }
    
    // Remove absolute path identifier (prevents the use of absolute paths)
    String normalized = path;
    if (normalized.startsWith("/") || normalized.startsWith("\\")) {
      normalized = normalized.substring(1);
    }
    
    // Normalized path separator (uniformly use /)
    normalized = normalized.replace("\\", "/");
    
    // Remove leading path separator
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    
    // Remove trailing path separator
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    
    // Check if it contains empty path segments (prevent // or ///)
    if (normalized.contains("//")) {
      throw new JeecgBootException("Invalid path format; consecutive path separators are not allowed");
    }
    
    // Check path traversal again (prevent path traversal after coding)
    if (normalized.contains("..")) {
      throw new JeecgBootException("Path contains illegal characters; path traversal is forbidden");
    }
    
    // Check if it contains a Windows drive letter (such as C: or D:)
    if (normalized.matches("^[A-Za-z]:.*")) {
      throw new JeecgBootException("Invalid path format; drive letters are not allowed");
    }
    
    // Whitelist check: only allows letters, numbers, Chinese characters, underlines, dashes, dots, slashes and backslashes, completely preventing path manipulation
    if (!normalized.matches("^[a-zA-Z0-9\\u4e00-\\u9fa5/\\\\_\\-\\.]+$")) {
      throw new JeecgBootException("Path contains illegal characters; ensure the path contains only valid characters");
    }
    
    return normalized;
  }

  /**
   * Verify whether the redirect URL is safe to prevent open redirect attacks
   * Only relative paths or URLs with the same domain name are allowed
   *
   * @param redirectUrl User-provided redirect URL
   * @param request current request object, used to obtain the current domain name
   * @return Validated safe URL, or null if unsafe
   */
  public static String validateRedirectUrl(String redirectUrl, HttpServletRequest request) {
    if (oConvertUtils.isEmpty(redirectUrl)) {
      return null;
    }

    try {
      // If it is a relative path (starting with /), it is considered safe
      if (redirectUrl.startsWith("/")) {
        // Ensure relative paths contain no scheme or host
        if (redirectUrl.contains("://") || redirectUrl.contains("//")) {
          log.warn("Detected unsafe relative path redirect: {}", redirectUrl);
          return null;
        }
        return redirectUrl;
      }

      // If it is a complete URL, verify whether it is the same domain name
      URL url = new URL(redirectUrl);
      String urlHost = url.getHost();
      
      // Get the currently requested domain name
      String currentHost = request.getServerName();
      String currentScheme = request.getScheme();
      int currentPort = request.getServerPort();
      
      // Build the full domain name of the current request (including port)
      String currentFullHost = currentHost;
      if ((currentScheme.equals("http") && currentPort != 80) 
          || (currentScheme.equals("https") && currentPort != 443)) {
        currentFullHost = currentHost + ":" + currentPort;
      }
      
      // Build the full domain name of the URL (including port)
      String urlFullHost = urlHost;
      int urlPort = url.getPort();
      if (urlPort != -1) {
        urlFullHost = urlHost + ":" + urlPort;
      } else {
        // If no port is specified, the protocol's default port is used
        String urlScheme = url.getProtocol();
        if (urlScheme.equals("http")) {
          urlFullHost = urlHost + ":80";
        } else if (urlScheme.equals("https")) {
          urlFullHost = urlHost + ":443";
        }
      }
      
      // Compare domain names (case insensitive)
      if (currentFullHost.equalsIgnoreCase(urlFullHost)) {
        return redirectUrl;
      }
      
      log.warn("Business alert", currentFullHost, urlFullHost);
      return null;
      
    } catch (MalformedURLException e) {
      log.warn("Business alert", redirectUrl);
      return null;
    }
  }

  /**
   * Verify redirect URL is safe (simplified version, only relative paths allowed)
   *
   * @param redirectUrl User-provided redirect URL
   * @return Validated safe URL, or null if unsafe
   */
  public static String validateRedirectUrl(String redirectUrl) {
    if (oConvertUtils.isEmpty(redirectUrl)) {
      return null;
    }

    // Only relative paths (starting with /) are allowed
    if (redirectUrl.startsWith("/")) {
      // Ensure relative paths contain no scheme or host
      if (redirectUrl.contains("://") || redirectUrl.matches("^//.*")) {
        log.warn("Detected unsafe relative path redirect: {}", redirectUrl);
        return null;
      }
      return redirectUrl;
    }

    // Absolute URLs are not allowed
    log.warn("Detected absolute URL redirect, rejected: {}", redirectUrl);
    return null;
  }

  /**
   * Determine whether the File name has a drive letter and reprocess it.
   *
   * @param fileName
   * @return
   */
  public static String getFileName(String fileName) {
    // Determine whether there is drive letter information
    // Check for Unix-style path
    int unixSep = fileName.lastIndexOf('/');
    // Check for Windows-style path
    int winSep = fileName.lastIndexOf('\\');
    // Cut off at latest possible point
    int pos = (winSep > unixSep ? winSep : unixSep);
    if (pos != -1) {
      // Any sort of path separator found...
      fileName = fileName.substring(pos + 1);
    }
    // Replace special characters in uploaded File name
    fileName =
        fileName
            .replace("=", "")
            .replace(",", "")
            .replace("&", "")
            .replace("#", "")
            .replace("“", "")
            .replace("”", "");
    // Replace the spaces in the uploaded File name
    fileName = fileName.replaceAll("\\s", "");
    // update-beign-author:taoyan date:20220302 for: /issues/3381 online online form When using the file Component, the uploaded File name contains %, and the download is abnormal.
    fileName = fileName.replace(FILE_NAME_REGEX, "");
    // update-end-author:taoyan date:20220302 for: /issues/3381 online online form When using the file Component, the uploaded File name contains %, and the download is abnormal.
    return fileName;
  }

  /**
   * Java determines whether a string contains Chinese characters
   *
   * @param str
   * @return
   */
  public static boolean ifContainChinese(String str) {
    if (str.getBytes(StandardCharsets.UTF_8).length == str.length()) {
      return false;
    } else {
      Matcher m = ZHONGWEN_PATTERN.matcher(str);
      if (m.find()) {
        return true;
      }
      return false;
    }
  }

  /** Unified global upload @Return: java.lang.String */
  public static String upload(MultipartFile file, String bizPath, String uploadType) {
    // Validate bizPath securely to prevent path traversal
    if (oConvertUtils.isNotEmpty(bizPath)) {
      try {
        bizPath = sanitizePath(bizPath);
      } catch (JeecgBootException e) {
        throw new JeecgBootException("Upload directory bizPath, invalid format!", e);
      }
    }
    String url = "";
    try {
      if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        url = MinioUtil.upload(file, bizPath);
      } else {
        url = OssBootUtil.upload(file, bizPath);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
      throw new JeecgBootException(e.getMessage());
    }
    return url;
  }

  /**
   * Local file upload
   *
   * @param mf file
   * @param bizPath Custom path
   * @return
   */
  public static String uploadLocal(MultipartFile mf, String bizPath, String uploadpath) {
    try {
      // update-begin-author:liusq date:20210809 for: filter upload file types (the extension is subject to Content sniffing SECZ-SSCSP-CODE-040)
      String contentExt = SsrfFileTypeFilter.validateUploadAndGetExtension(mf);
      // update-end-author:liusq date:20210809 for: filter uploaded file types
      // Validate bizPath securely to prevent path traversal
      if (oConvertUtils.isNotEmpty(bizPath)) {
        bizPath = sanitizePath(bizPath);
      }
      String fileName = null;
      File file = new File(uploadpath + File.separator + bizPath + File.separator);
      if (!file.exists()) {
        // Create file root directory
        boolean created = file.mkdirs();
          if (!created && !file.exists()) {
              log.warn("Failed to create directory: " + file.getAbsolutePath());
          }
      }
      String orgName = mf.getOriginalFilename();
      String secureName = FileNameUtil.generateSecureFileName(orgName);
      if (secureName.startsWith("/")) {
        secureName = secureName.substring(1);
      }
      // If the extension is removed from the whitelist, fall back to the extension inferred by ContentType.
      if (!secureName.contains(".") && contentExt != null && !contentExt.isEmpty()) {
        secureName = secureName + "." + contentExt;
      }
      fileName = secureName;
      String savePath = file.getPath() + File.separator + fileName;
      File savefile = new File(savePath);
      FileCopyUtils.copy(mf.getBytes(), savefile);
      String dbpath = null;
      if (oConvertUtils.isNotEmpty(bizPath)) {
        dbpath = bizPath + File.separator + fileName;
      } else {
        dbpath = fileName;
      }
      if (dbpath.contains(SymbolConstant.DOUBLE_BACKSLASH)) {
        dbpath = dbpath.replace("\\", "/");
      }
      return dbpath;
    } catch (IOException e) {
      log.error("operation failed", e);
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return "";
  }

  /** Unified global upload with bucket @Return: java.lang.String */
  public static String upload(
      MultipartFile file, String bizPath, String uploadType, String customBucket) {
    // Validate bizPath securely to prevent path traversal
    if (oConvertUtils.isNotEmpty(bizPath)) {
      try {
        bizPath = sanitizePath(bizPath);
      } catch (JeecgBootException e) {
        throw new JeecgBootException("Upload directory bizPath, invalid format!", e);
      }
    }
    String url = "";
    try {
      if (CommonConstant.UPLOAD_TYPE_MINIO.equals(uploadType)) {
        url = MinioUtil.upload(file, bizPath, customBucket);
      } else {
        url = OssBootUtil.upload(file, bizPath, customBucket);
      }
    } catch (Exception e) {
      log.error("operation failed", e);
    }
    return url;
  }

  /** CurrentSystemDatabaseType */
  private static String DB_TYPE = "";

  private static DbType dbTypeEnum = null;

  /**
   * Globally obtain the platform Database type (obsolete)
   *
   * @return
   */
  @Deprecated
  public static String getDatabaseType() {
    if (oConvertUtils.isNotEmpty(DB_TYPE)) {
      return DB_TYPE;
    }
    DataSource dataSource = SpringContextUtils.getApplicationContext().getBean(DataSource.class);
    try {
      return getDatabaseTypeByDataSource(dataSource);
    } catch (SQLException e) {
      // log.error("operation failed", e);
      log.warn("operation failed", e);
      return "";
    }
  }

  /**
   * Globally obtain the platform Database type (corresponding to mybaisPlus enumeration)
   *
   * @return
   */
  public static DbType getDatabaseTypeEnum() {
    if (oConvertUtils.isNotEmpty(dbTypeEnum)) {
      return dbTypeEnum;
    }
    try {
      DataSource dataSource = SpringContextUtils.getApplicationContext().getBean(DataSource.class);
      try (Connection connection = dataSource.getConnection()) {
        dbTypeEnum = JdbcUtils.getDbType(connection.getMetaData().getURL());
      }
      return dbTypeEnum;
    } catch (SQLException e) {
      log.warn("operation failed", e);
      return null;
    }
  }

  /**
   * Get DataSourceProperty based on data source key
   *
   * @param sourceKey
   * @return
   */
  public static DataSourceProperty getDataSourceProperty(String sourceKey) {
    DynamicDataSourceProperties prop =
        SpringContextUtils.getApplicationContext().getBean(DynamicDataSourceProperties.class);
    Map<String, DataSourceProperty> map = prop.getDatasource();
    DataSourceProperty db = (DataSourceProperty) map.get(sourceKey);
    return db;
  }

  /**
   * Get data source connection based on sourceKey
   *
   * @param sourceKey
   * @return
   * @throws SQLException
   */
  public static Connection getDataSourceConnect(String sourceKey) throws SQLException {
    if (oConvertUtils.isEmpty(sourceKey)) {
      sourceKey = "master";
    }
    DynamicDataSourceProperties prop =
        SpringContextUtils.getApplicationContext().getBean(DynamicDataSourceProperties.class);
    Map<String, DataSourceProperty> map = prop.getDatasource();
    DataSourceProperty db = (DataSourceProperty) map.get(sourceKey);
    if (db == null) {
      return null;
    }
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName(db.getDriverClassName());
    ds.setUrl(db.getUrl());
    ds.setUsername(db.getUsername());
    ds.setPassword(db.getPassword());
    return ds.getConnection();
  }

  /**
   * GetDatabaseType
   *
   * @param dataSource
   * @return
   * @throws SQLException
   */
  private static String getDatabaseTypeByDataSource(DataSource dataSource) throws SQLException {
    if ("".equals(DB_TYPE)) {
      Connection connection = dataSource.getConnection();
      try {
        DatabaseMetaData md = connection.getMetaData();
        String dbType = md.getDatabaseProductName().toUpperCase();
        String sqlserver = "SQL SERVER";
        if (dbType.indexOf(DataBaseConstant.DB_TYPE_MYSQL) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_MYSQL;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_ORACLE) >= 0
            || dbType.indexOf(DataBaseConstant.DB_TYPE_DM) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_ORACLE;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_SQLSERVER) >= 0
            || dbType.indexOf(sqlserver) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_SQLSERVER;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_POSTGRESQL) >= 0
            || dbType.indexOf(DataBaseConstant.DB_TYPE_KINGBASEES) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_POSTGRESQL;
        } else if (dbType.indexOf(DataBaseConstant.DB_TYPE_MARIADB) >= 0) {
          DB_TYPE = DataBaseConstant.DB_TYPE_MARIADB;
        } else {
          log.error("DatabaseType:[" + dbType + "] not recognized!");
          // throw new JeecgBootException("Unrecognized database type:["+dbType+"]!");
        }
      } catch (Exception e) {
        log.error("operation failed", e);
      } finally {
        connection.close();
      }
    }
    return DB_TYPE;
  }

  /**
   * Get server address
   *
   * @param request
   * @return
   */
  public static String getBaseUrl(HttpServletRequest request) {
    // 1. [Compatible] Compatible with the base path under microservices-------
    String xGatewayBasePath = request.getHeader(ServiceNameConstants.X_GATEWAY_BASE_PATH);
    if (oConvertUtils.isNotEmpty(xGatewayBasePath)) {
      log.info("x_gateway_base_path = " + xGatewayBasePath);
      return xGatewayBasePath;
    }
    // 2. [Compat] After SSL termination, request.getScheme() may not return https
    // https://blog.csdn.net/weixin_34376986/article/details/89767950
    String scheme = request.getHeader(CommonConstant.X_FORWARDED_SCHEME);
    if (oConvertUtils.isEmpty(scheme)) {
      scheme = request.getScheme();
    }

    // 3. Routine operations
    String serverName = request.getServerName();
    int serverPort = request.getServerPort();
    String contextPath = request.getContextPath();

    // Return host domain
    String baseDomainPath = null;
    // update-begin---author:wangshuai---date:2024-03-15---for:[QQYUN-8561] WeCom (Enterprise WeChat) login request interface setting context is inconsistent, resulting in interface 404---
    int httpPort = 80;
    int httpsPort = 443;
    if (httpPort == serverPort || httpsPort == serverPort) {
      // update-end---author:wangshuai---date:2024-03-15---for:【QQYUN-8561】WeCom (Enterprise WeChat) login request interface setting context is inconsistent, resulting in interface 404---~
      baseDomainPath = scheme + "://" + serverName + contextPath;
    } else {
      baseDomainPath = scheme + "://" + serverName + ":" + serverPort + contextPath;
    }
    log.debug("-----Common getBaseUrl----- : " + baseDomainPath);
    return baseDomainPath;
  }

  /**
   * Recursively merge fastJSON objects
   *
   * @param target Target object
   * @param sources source objects, multiple are allowed, priority is from left to right, the rightmost one has the highest priority
   */
  public static JSONObject mergeJSON(JSONObject target, JSONObject... sources) {
    for (JSONObject source : sources) {
      CommonUtils.mergeJSON(target, source);
    }
    return target;
  }

  /**
   * Recursively merge fastJSON objects
   *
   * @param target Target object
   * @param source source object
   */
  public static JSONObject mergeJSON(JSONObject target, JSONObject source) {
    for (String key : source.keySet()) {
      Object sourceItem = source.get(key);
      // Whether it is a JSONObject
      if (sourceItem instanceof Map) {
        // This key exists in the target
        if (target.containsKey(key)) {
          // Both are JSONObject, continue to merge
          if (target.get(key) instanceof Map) {
            CommonUtils.mergeJSON(target.getJSONObject(key), source.getJSONObject(key));
            continue;
          }
        }
      }
      // This key does not exist in the target, or it is not a JSONObject, then it will be overwritten.
      target.put(key, sourceItem);
    }
    return target;
  }

  /**
   * Split the list collection using separators
   *
   * @param list StringType collection text
   * @param separator separator
   * @return
   */
  public static String getSplitText(List<String> list, String separator) {
    if (null != list && list.size() > 0) {
      return StringUtils.join(list, separator);
    }
    return "";
  }

  /**
   * ConditionSQL via table
   *
   * @param tableSql sys_user where name = '1212'
   * @return name = '1212'
   */
  public static String getFilterSqlByTableSql(String tableSql) {
    if (oConvertUtils.isEmpty(tableSql)) {
      return null;
    }

    if (tableSql.toLowerCase().indexOf(DataBaseConstant.SQL_WHERE) > 0) {
      String[] arr = tableSql.split(" (?i)where ");
      if (arr != null && oConvertUtils.isNotEmpty(arr[1])) {
        return arr[1];
      }
    }
    return "";
  }

  /**
   * Through tableGet table name
   *
   * @param tableSql sys_user where name = '1212'
   * @return sys_user
   */
  public static String getTableNameByTableSql(String tableSql) {
    if (oConvertUtils.isEmpty(tableSql)) {
      return null;
    }

    if (tableSql.toLowerCase().indexOf(DataBaseConstant.SQL_WHERE) > 0) {
      String[] arr = tableSql.split(" (?i)where ");
      return arr[0].trim();
    } else {
      return tableSql;
    }
  }

  /**
   * Determine whether two arrays intersect
   *
   * @param set1
   * @param arr2
   * @return
   */
  public static boolean hasIntersection(Set<String> set1, String[] arr2) {
    if (set1 == null) {
      return false;
    }

    if (set1.size() > 0) {
      for (String str : arr2) {
        if (set1.contains(str)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Outputting the info log will capture exceptions to prevent program exceptions caused by log problems.
   *
   * @param msg
   * @param objects
   */
  public static void logInfo(String msg, Object... objects) {
    try {
      log.info(msg, objects);
    } catch (Exception e) {
      log.warn("{} —— {}", msg);
    }
  }

  public static String convertImage(String file) {

    String[] values = file.split(",");

    for (int i = 0; i < values.length; i++) {

      if (values[i].lastIndexOf("?") == -1) {
        continue;
      }

      values[i] = values[i].substring(0, values[i].lastIndexOf("?"));
    }

    return java.util.Arrays.stream(values).collect(java.util.stream.Collectors.joining(","));
  }

  private static final Pattern OBJECT_ID_QUERY_PARAM =
      Pattern.compile("(?i)(?:\\?|&|^)objectId=([^&#]+)");

  /** Canonical OSS objectId: {@code upload/yyyyMMdd/<filename>}. */
  private static final Pattern VALID_UPLOAD_OBJECT_ID =
      Pattern.compile("^upload/\\d{8}/[^\\s\"'<>]+$", Pattern.CASE_INSENSITIVE);

  /**
   * Repair missing slash after date folder: {@code upload/20260922file_x.jpeg} →
   * {@code upload/20260922/file_x.jpeg} (x-file-storage path without trailing slash).
   */
  private static final Pattern MISSING_DATE_SLASH =
      Pattern.compile("(?i)^(upload/\\d{8})(?!/)(.+)$");

  /**
   * Normalize preview URL/plain objectId (comma separated) to objectId only.
   *
   * <p>Compatible with:
   * <ul>
   *   <li>{@code http(s)://.../sys/common/file?objectId=upload/...}</li>
   *   <li>Relative path {@code /api/sys/common/file?objectId=...}</li>
   *   <li>Pure objectId {@code upload/yyyyMMdd/xxx.pdf} already stored in the library</li>
   * </ul>
   *
   * <p>Rejects non-file values such as API error messages (e.g. login-timeout i18n text) that would
   * otherwise be persisted and later trigger {@code /sys/common/file} 400.
   */
  public static String convertObjectId(String file) {

    if (StrUtil.isBlank(file)) {
      return null;
    }

    String[] files = file.split(",");

    String[] objectIds = new String[files.length];

    int i = 0;

    for (String item : files) {
      String trimmed = StrUtil.trim(item);
      if (StrUtil.isBlank(trimmed)) {
        throw new IllegalArgumentException("objectId is required");
      }

      String objectId = null;

      try {
        String query = URLUtil.url(trimmed).getQuery();
        if (StrUtil.isNotBlank(query)) {
          Map<String, String> paramMap =
              HttpUtil.decodeParamMap(query, Charset.defaultCharset());
          objectId = paramMap.get("objectId");
        }
      } catch (Exception ignored) {
        // fall through to regex / raw objectId
      }

      if (StrUtil.isBlank(objectId)) {
        Matcher matcher = OBJECT_ID_QUERY_PARAM.matcher(trimmed);
        if (matcher.find()) {
          try {
            objectId = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8.name());
          } catch (Exception e) {
            objectId = matcher.group(1);
          }
        }
      }

      if (StrUtil.isBlank(objectId)) {
        // It is a pure objectId (backfill from DB when editing and then normalize will go here)
        if (StrUtil.startWithIgnoreCase(trimmed, "http://")
            || StrUtil.startWithIgnoreCase(trimmed, "https://")) {
          throw new IllegalArgumentException("objectId is required");
        }
        objectId = trimmed;
        while (objectId.startsWith("/")) {
          objectId = objectId.substring(1);
        }
      }

      objectId = normalizeUploadObjectId(objectId);
      if (!isValidUploadObjectId(objectId)) {
        throw new IllegalArgumentException("invalid objectId: " + objectId);
      }

      objectIds[i] = objectId;

      i++;
    }

    return java.util.Arrays.stream(objectIds).collect(java.util.stream.Collectors.joining(","));
  }

  /** True when value matches canonical {@code upload/yyyyMMdd/<name>} objectId. */
  public static boolean isValidUploadObjectId(String objectId) {
    return StrUtil.isNotBlank(objectId) && VALID_UPLOAD_OBJECT_ID.matcher(objectId.trim()).matches();
  }

  /**
   * Insert missing {@code /} after {@code upload/yyyyMMdd} when path was concatenated without
   * trailing slash.
   */
  public static String normalizeUploadObjectId(String objectId) {
    if (StrUtil.isBlank(objectId)) {
      return objectId;
    }
    String normalized = objectId.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    Matcher missingSlash = MISSING_DATE_SLASH.matcher(normalized);
    if (missingSlash.matches() && !isValidUploadObjectId(normalized)) {
      normalized = missingSlash.group(1) + "/" + missingSlash.group(2);
    }
    return normalized;
  }

  public static String convertObjectIdByDomain(String fileDomain) {

    Matcher matcher = PATH_PATTERN.matcher(fileDomain);

    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid URL format: " + fileDomain);
    }

    String objectId = matcher.group(1);
    while (objectId.startsWith("/")) {
      objectId = objectId.substring(1);
    }
    return normalizeUploadObjectId(objectId);
  }

  /**
   * Generate invitation code
   *
   * @param length Invitation code length, recommended 6-12
   * @return invitation code
   */
  public static String generateInviteCode(int length) {
    if (length < 6 || length > 12) {
      throw new IllegalArgumentException("Invitation code length must be between 6 and 12");
    }

    // Basic character set to avoid confusing characters (0, O, 1, I)
    String baseChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    // Generate unique base string: combine UUID, nanosecond time, thread ID and Random number
    StringBuilder rawCode = new StringBuilder();
    rawCode.append(IdUtil.fastSimpleUUID()); // Efficient UUID
    rawCode.append(System.nanoTime()); // nanosecond time
    rawCode.append(Thread.currentThread().getId()); // Thread ID
    rawCode.append(RandomUtil.randomLong()); // Additional Random number

    // Use national secret SM3 hashing to enhance randomness
    String hash = SmUtil.sm3(rawCode.toString());

    // Use hash value as seed, map to baseChars
    StringBuilder inviteCode = new StringBuilder();
    long seed = Math.abs(hash.hashCode());
    for (int i = 0; i < length; i++) {
      // Use SecureRandom to enhance randomness
      int index =
          RandomUtil.getSecureRandom(String.valueOf(seed).getBytes(StandardCharsets.UTF_8)).nextInt(baseChars.length());
      inviteCode.append(baseChars.charAt(index));
      // Update seed, combine hash fragments
      seed = Math.abs((seed + hash.charAt(i % hash.length())) % Long.MAX_VALUE);
    }

    // Ensure length, padding or truncation
    String result = StrUtil.sub(inviteCode.toString(), 0, length);
    if (result.length() < length) {
      result = StrUtil.padAfter(result, length, RandomUtil.randomChar(baseChars));
    }

    return result.toUpperCase();
  }

  /** Determine whether the current is a web request environment */
  public static boolean isWebEnvironment() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null;
  }

//   public static void main(String[] args) {
//     //Example: Generate 8-digit invitation code
//     for (int i = 0; i < 5; i++) {
//       log.info(String.valueOf("Invitation Code: " + generateInviteCode(8)));
//     }
//   }
}
