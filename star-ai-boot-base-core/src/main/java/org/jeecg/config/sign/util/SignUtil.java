package org.jeecg.config.sign.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.SymbolConstant;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.SecureHashUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.springframework.util.StringUtils;

import java.util.SortedMap;

/**
 * Signature utility
 *
 * @author jeecg
 * @date 20210621
 */
@Slf4j
public class SignUtil {
  public static final String X_PATH_VARIABLE = "x-path-variable";

  /**
   * @param params all request params sorted and signed here
   * @return signature verification result
   */
  public static boolean verifySign(SortedMap<String, String> params, String headerSign) {
    if (params == null || StringUtils.isEmpty(headerSign)) {
      return false;
    }
    // Encrypt parameters
    String paramsSign = getParamsSign(params);
    return !StringUtils.isEmpty(paramsSign) && headerSign.equals(paramsSign);
  }

  /**
   * @param params all request params sorted and signed here
   * @return signature
   */
  public static String getParamsSign(SortedMap<String, String> params) {
    // Remove timestamp from URL
    params.remove("_t");
    String paramsJsonStr = JSONObject.toJSONString(params);
    // Set signing key
    JeecgBaseConfig jeecgBaseConfig = SpringContextUtils.getBean(JeecgBaseConfig.class);
    String signatureSecret = jeecgBaseConfig.getSignatureSecret();
    String curlyBracket = SymbolConstant.DOLLAR + SymbolConstant.LEFT_CURLY_BRACKET;
    if (oConvertUtils.isEmpty(signatureSecret) || signatureSecret.contains(curlyBracket)) {
      throw new JeecgBootException("Signature secret ${jeecg.signatureSecret} is not configured!!");
    }
    try {
      // [issues/I484RW] After 2.4.6 deployment, the drop-down search box prompts "sign signature verification failed"
      // Security fix: Replace insecure MD5 with HMAC-SHA256, providing stronger security and anti-collision capabilities
      // Note: This change will cause the signature algorithm to be incompatible, and the client signature algorithm needs to be updated simultaneously.
      String hmacSign = SecureHashUtil.hmacSha256Sign(paramsJsonStr, signatureSecret, "UTF-8");
      return hmacSign != null ? hmacSign.toUpperCase() : null;
    } catch (Exception e) {
      log.error("operation failed", e);
      return null;
    }
  }
}
