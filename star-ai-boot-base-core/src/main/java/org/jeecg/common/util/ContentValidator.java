package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.owasp.esapi.codecs.MySQLCodec.Mode;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

@Slf4j
public class ContentValidator {

  private static final PolicyFactory XSS_POLICY = Sanitizers.FORMATTING.and(Sanitizers.LINKS);
  private static final List<String> ILLEGAL_PATTERNS =
      Arrays.asList(
          "SELECT\\s",
          "INSERT\\s",
          "DELETE\\s",
          "UPDATE\\s",
          "DROP\\s",
          "--",
          ";",
          "\\*",
          "<script>",
          "</script>",
          "alert\\(",
          "onerror\\(",
          "onload\\(");

  public static boolean isSqlQuery(String value) {
    // Regular expressions that define common SQL keywords
    Pattern sqlPattern =
        Pattern.compile(
            "\\b(select|insert|update|delete|drop|alter|create|replace|order by|group by|union|join)\\b",
            Pattern.CASE_INSENSITIVE);

    // Checks whether the input string matches the pattern of the SQL query
    return sqlPattern.matcher(value).find();
  }

  public static void validateSqlInput(String value, String clientIp) {
    // Define regular expressions that match SQL Fields or keywords
    Pattern allowedPattern = Pattern.compile("^[a-zA-Z0-9_]+$");

    // Split input string by common SQL delimiters (comma, equal sign, space)
    String[] tokens = value.split("[,= ]+");

    // check each Field or keyword
    for (String token : tokens) {
      // If it is not in the whitelist, check whether it matches the allowed Field format
      if (!allowedPattern.matcher(token).matches()) {
        log.info("Unexpected SQL field or keyword detected: {}", token);
        throw new SecurityException("Unexpected SQL field or keyword detected: " + token);
      }
    }

    // Date format validation (YYYY-MM-DD)
    if (value.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
      return; // Skip the check directly
    }

    // If it is a legal list of numbers, skip checking
    if (value.matches("^(-?\\d+)(,-?\\d+)*$")) {
      log.info("Input [{}] is a valid numeric list, skipping SQL injection check.", value);
      return;
    }

    // SQL injection coding check
    String encoded = ESAPI.encoder().encodeForSQL(new MySQLCodec(Mode.STANDARD), value);
    if (!encoded.equals(value)) {
      log.warn("Potential SQL injection detected from IP [{}]: {}", clientIp, value);
      throw new SecurityException("Potential SQL injection detected: " + value);
    }

    validateIllegalPatterns(value, clientIp);
  }

  private static void validateIllegalPatterns(String value, String clientIp) {
    for (String pattern : ILLEGAL_PATTERNS) {
      if (value.matches("(?i).*" + pattern + ".*")) {
        log.error("Illegal content detected from IP [{}]: {}", clientIp, value);
        throw new SecurityException("Illegal content detected: " + value);
      }
    }
  }

  // check single string
  public static void validateString(String value, String clientIp, String baseURL, String key) {
    // If the input is valid JSON data, skip XSS checking
    if (JSONUtil.isTypeJSONArray(value) || JSONUtil.isTypeJSONObject(value)) {
      log.info("Valid JSON detected, skipping XSS check: {}", value);
      return;
    }

    // If it is SQL query mode, skip XSS check
    if (isSqlQuery(value)) {
      validateSqlInput(value, clientIp); // Perform SQL check
      return;
    }

    if (value.matches("^\\*?[A-Za-z0-9]+(\\*[A-Za-z0-9]+)?\\*?$")) {
      return;
    }

    // Check for illegal keywords
    for (String pattern : ILLEGAL_PATTERNS) {
      if (value.matches("(?i).*" + pattern + ".*")) {
        log.warn("Illegal content detected from IP [{}]: {} {}", clientIp, value, key);
        throw new SecurityException("Illegal content detected: " + value);
      }
    }

    if (value.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
      return; // Skip further checks
    }

    if (value.startsWith(baseURL)) {
      return;
    }

    // XSS Cleanup
    String sanitized = XSS_POLICY.sanitize(value);
    if (!sanitized.equals(value)) {
      log.warn("Potential XSS content detected from IP [{}]: {} {}", clientIp, value, key);
      throw new SecurityException("Potential XSS content detected: " + value);
    }
  }

  public static void validateRequestBody(
      String json, String clientIp, String baseURL, Map<String, String> customFieldValidationMap) {
    if (JSONUtil.isTypeJSONArray(json)) {
      JSONArray jsonArray = JSONUtil.parseArray(json);
      for (Object i : jsonArray) {
        if (i instanceof JSONObject) {
          validateJsonData((JSONObject) i, clientIp, baseURL, customFieldValidationMap);
        } else if (i instanceof String) {
          validateString((String) i, clientIp, baseURL, null);
        }
      }
    } else if (JSONUtil.isTypeJSONObject(json)) {
      JSONObject jsonObject = JSONUtil.parseObj(json);
      validateJsonData(jsonObject, clientIp, baseURL, customFieldValidationMap);
    }
  }

  private static void validateJsonData(
      JSONObject jsonObject,
      String clientIp,
      String baseURL,
      Map<String, String> customFieldValidation) {
    for (String key : jsonObject.keySet()) {
      Object value = jsonObject.get(key);
      if (value instanceof JSONObject) {
        validateJsonData((JSONObject) value, clientIp, baseURL, customFieldValidation);
      } else if (value instanceof String) {
        if (validateCustom(key, (String) value, clientIp, customFieldValidation)) {
          validateString((String) value, clientIp, baseURL,key);
        }
      }
    }
  }

  public static boolean validateCustom(
      String key, String value, String clientIp, Map<String, String> customFieldValidation) {
    if (StrUtil.isBlank(key) || !customFieldValidation.containsKey(key.toLowerCase())) {
      return true;
    }

    String pattern = customFieldValidation.get(key.toLowerCase());
    if (!StrUtil.equals(pattern, "*") && !value.matches(pattern)) {
      throw new SecurityException("Illegal content detected: " + value);
    }
    return false;
  }
}
