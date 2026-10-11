package org.jeecg.common.aspect.config;

import lombok.Data;
import org.jeecg.common.aspect.enumeration.SecondAuthType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * second-factor authconfiguration class
 * Supports the second-factor authtype of the unified management interface through the configurationfile
 * 
 * Config example (application.yml):
 * jeecg:
 *   second-auth:
 *     enabled: true
 *     mappings:
 *       "/sys/user/updatePassword": VERIFICATION_CODE_ONLY
 *       "/sys/account/withdraw": BOTH_REQUIRED
 *       "/sys/account/transfer": PAYMENT_PASSWORD_ONLY
 * 
 * @author jeecg-boot
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jeecg.second-auth")
public class SecondAuthConfig {

  /**
   * Whether to enable secondary authentication in configuration mode
   */
  private boolean enabled = true;

  /**
   * Mapping of interface paths and authentication types
   * key: Interface path (supports Ant-style pathmatch, such as/sys/user/* *)
   * value: Authentication type (verification_code_only, payment_password_only, both_required)
   */
  private Map<String, String> mappings = new HashMap<>();

  /**
   * default authentication type (used when the interface is not configured)
   */
  private String defaultAuthType = "VERIFICATION_CODE_ONLY";

  /**
   * Authenticate type according to interface pathget
   * 
   * @ param path interface path
   * @ return authentication type, returndefaulttype if not configured
   */
  public SecondAuthType getAuthType(String path) {
    if (!enabled || mappings == null || mappings.isEmpty()) {
      return SecondAuthType.fromCode(defaultAuthType);
    }

    // Exact match
    String authType = mappings.get(path);
    if (authType != null) {
      return SecondAuthType.fromCode(authType);
    }

    // Ant-style path matching
    for (Map.Entry<String, String> entry : mappings.entrySet()) {
      String pattern = entry.getKey();
      if (matchPath(pattern, path)) {
        return SecondAuthType.fromCode(entry.getValue());
      }
    }

    // returndefaulttype
    return SecondAuthType.fromCode(defaultAuthType);
  }

  /**
   * Ant-style path matching
   * Supports * and ** wildcards
   * * match single-layer path, * * match multi-layer path
   */
  private boolean matchPath(String pattern, String path) {
    if (pattern.equals(path)) {
      return true;
    }

    // process * * Wildcard (match multilayer path)
    if (pattern.contains("**")) {
      String regex = pattern
          .replace(".", "\\.")
          .replace("**", ".*")
          .replace("*", "[^/]*"); // * No matchpath separator
      return path.matches(regex);
    }

    // process * wildcard (match single-layer path)
    if (pattern.contains("*")) {
      // Replace * with regex but not match path separator
      String regex = pattern
          .replace(".", "\\.")
          .replace("*", "[^/]*");
      return path.matches(regex);
    }

    return false;
  }

  /**
   * check whether the second-factor auth is required for the interface
   */
  public boolean requiresSecondAuth(String path) {
    if (!enabled) {
      return false;
    }
    
    // If configuration is mapped, it is assumed that second-factor auth is required
    if (mappings != null && !mappings.isEmpty()) {
      // checkexactmatch
      if (mappings.containsKey(path)) {
        return true;
      }
      
      // checkpathmatch
      for (String pattern : mappings.keySet()) {
        if (matchPath(pattern, path)) {
          return true;
        }
      }
    }
    
    return false;
  }
}















