package org.jeecg.config.shiro;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.ServiceNameConstants;
import org.jeecg.common.util.JwtRs256SecureUtil;
import org.jeecg.common.util.RequestUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
public class StpLogicJwtForRs256 extends StpLogic {

  private static final String JWT_REVOKED_PREFIX = "jwt_revoked:";
  private static final String JWT_ACTIVE_PREFIX = "jwt_active:";
  private static final String FIXED_ISSUER_PROPERTY = "sa-token.jwt.issuer";
  private static final String DEFAULT_FIXED_ISSUER = "https://mpay.xnow.xyz";
  private static final String FIXED_AUDIENCE = "mightyPay-boot";
  private static final String JWT_VERSION_CLAIM = "jti_ver";
  private static final String CURRENT_JWT_VERSION = "v2";

  private String apiSubdomainAddress;

  public StpLogicJwtForRs256(String apiSubdomainAddress) {
    super("jwt-rs256");
    this.apiSubdomainAddress = apiSubdomainAddress;
  }

  @Override
  public String createTokenValue(
      Object loginId, String device, long timeout, Map<String, Object> extraData) {
    String issuer = resolveFixedIssuer();
    String loginIdStr = loginId.toString();
    long ttlSeconds = timeout > 0 ? timeout : 7200L;

    Map<String, Object> claims = new HashMap<>();
    claims.put(JWT_VERSION_CLAIM, CURRENT_JWT_VERSION);
    if (extraData != null) {
      for (Map.Entry<String, Object> e : extraData.entrySet()) {
        if (e.getValue() != null) {
          claims.put(e.getKey(), e.getValue());
        }
      }
    }

    String token =
        JwtRs256SecureUtil.generateToken(
            loginIdStr, ttlSeconds, issuer, FIXED_AUDIENCE, claims);
    try {
      DecodedJWT decoded = JWT.decode(token);
      String jti = decoded.getId();
      if (StrUtil.isNotBlank(jti)) {
        StringRedisTemplate redis = getRedisTemplate();
        if (redis != null) {
          String activeKey = JWT_ACTIVE_PREFIX + loginIdStr;
          redis.opsForSet().add(activeKey, jti);
          redis.expire(activeKey, Duration.ofSeconds(ttlSeconds + 60L));
        }
      }
    } catch (Exception ignore) {
    }
    return token;
  }

  @Override
  public Object getLoginIdByToken(String tokenValue) {
    String expectedIssuer = resolveFixedIssuer();
    String tokenIssuer = "";
    String requestPath = "";
    try {
      HttpServletRequest currentRequest = RequestUtils.getCurrentRequest();
      if (currentRequest != null) {
        requestPath = currentRequest.getRequestURI();
      }
      DecodedJWT decoded = JWT.decode(tokenValue);
      tokenIssuer = decoded.getIssuer();
      String jti = decoded.getId();
      if (StrUtil.isNotBlank(jti)) {
        StringRedisTemplate redis = getRedisTemplate();
        if (redis != null
            && Boolean.TRUE.equals(redis.hasKey(JWT_REVOKED_PREFIX + jti))) {
          log.warn(
              "JWT_JTI_REVOKED jti={} sub={} path={}",
              jti,
              decoded.getSubject(),
              requestPath);
          throw NotLoginException.newInstance("jwt-rs256", "-jti-", "jti revoked", tokenValue);
        }
      }
      DecodedJWT jwt = JwtRs256SecureUtil.verifyToken(tokenValue, expectedIssuer);
      return jwt.getSubject();
    } catch (NotLoginException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "JWTValidateFailed: expectedIssuer={}, tokenIssuer={}, requestPath={}, publicKeyFp={}, err={}",
          expectedIssuer,
          tokenIssuer,
          requestPath,
          JwtRs256SecureUtil.publicKeyFingerprint(),
          e);
      throw NotLoginException.newInstance("jwt-rs256", "", "Invalid token", tokenValue);
    }
  }

  private String resolveFixedIssuer() {
    try {
      org.springframework.core.env.Environment env =
          SpringContextUtils.getApplicationContext().getEnvironment();
      String v = env.getProperty(FIXED_ISSUER_PROPERTY);
      if (StrUtil.isNotBlank(v)) {
        return v.trim();
      }
    } catch (Exception ignore) {
    }
    HttpServletRequest req = RequestUtils.getCurrentRequest();
    String fromHeader = buildExpectedIssuer(req);
    if (StrUtil.isNotBlank(fromHeader)) {
      return fromHeader;
    }
    return DEFAULT_FIXED_ISSUER;
  }

  private static StringRedisTemplate getRedisTemplate() {
    try {
      return SpringContextUtils.getApplicationContext().getBean(StringRedisTemplate.class);
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public String getTokenValue(boolean noPrefixThrowException) {
    String token = super.getTokenValue(noPrefixThrowException);

    if (token == null) {
      return null;
    }

    getLoginIdByToken(token);

    return token;
  }

  private String buildExpectedIssuer(HttpServletRequest request) {
    String basePath = resolveGatewayBasePath(request);
    if (StrUtil.isBlank(basePath)) {
      return "";
    }
    String subdomain = StrUtil.nullToEmpty(this.apiSubdomainAddress).trim();
    return basePath + subdomain;
  }

  private String resolveGatewayBasePath(HttpServletRequest request) {
    if (request == null) {
      return "";
    }
    String basePath = request.getHeader(ServiceNameConstants.X_GATEWAY_BASE_PATH);
    if (StrUtil.isBlank(basePath)) {
      basePath = request.getHeader("x_gateway_base_path");
    }
    if (StrUtil.isBlank(basePath)) {
      return "";
    }
    basePath = basePath.trim();
    if (StrUtil.equalsIgnoreCase(basePath, "null")) {
      return "";
    }
    while (basePath.endsWith("/")) {
      basePath = basePath.substring(0, basePath.length() - 1);
    }
    return basePath;
  }
}
