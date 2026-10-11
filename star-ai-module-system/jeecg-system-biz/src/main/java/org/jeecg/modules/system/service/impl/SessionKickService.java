package org.jeecg.modules.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionKickService {

  private final StringRedisTemplate redisTemplate;

  private static final String JWT_REVOKED_PREFIX = "jwt_revoked:";
  private static final String JWT_ACTIVE_PREFIX = "jwt_active:";
  private static final Duration REVOKE_TTL = Duration.ofDays(30);

  public void kickAllDevices(String userId, String reason) {
    if (StrUtil.isBlank(userId)) {
      return;
    }
    log.info("[SESSION_KICK] START uid={} reason={}", userId, reason);
    try {
      StpUtil.kickout(userId);
    } catch (Exception e) {
      log.warn("[SESSION_KICK] kickout failed uid={}: {}", userId, e.getMessage());
    }
    try {
      StpUtil.logout(userId);
    } catch (Exception e) {
      log.warn("[SESSION_KICK] logout failed uid={}: {}", userId, e.getMessage());
    }
    revokeActiveJtis(userId);
    log.info("[SESSION_KICK] DONE uid={} reason={}", userId, reason);
  }

  private void revokeActiveJtis(String userId) {
    String activeKey = JWT_ACTIVE_PREFIX + userId;
    try {
      Set<String> members = redisTemplate.opsForSet().members(activeKey);
      if (members != null) {
        for (String jti : members) {
          if (StrUtil.isBlank(jti)) continue;
          redisTemplate.opsForValue().set(JWT_REVOKED_PREFIX + jti, "1", REVOKE_TTL);
        }
        redisTemplate.delete(activeKey);
      }
    } catch (Exception e) {
      log.warn("[SESSION_KICK] revokeActiveJtis failed uid={}: {}", userId, e.getMessage());
    }
  }

  public void registerActiveJti(String userId, String jti, long ttlSeconds) {
    if (StrUtil.isBlank(userId) || StrUtil.isBlank(jti)) return;
    try {
      String activeKey = JWT_ACTIVE_PREFIX + userId;
      redisTemplate.opsForSet().add(activeKey, jti);
      redisTemplate.expire(activeKey, Duration.ofSeconds(Math.max(ttlSeconds, 60L)));
    } catch (Exception ignore) {
    }
  }

  public boolean isJtiRevoked(String jti) {
    if (StrUtil.isBlank(jti)) return false;
    try {
      return Boolean.TRUE.equals(redisTemplate.hasKey(JWT_REVOKED_PREFIX + jti));
    } catch (Exception e) {
      return false;
    }
  }

  public static String extractJti(String token) {
    if (StrUtil.isBlank(token)) return null;
    try {
      DecodedJWT decoded = JWT.decode(token);
      return decoded.getId();
    } catch (Exception e) {
      return null;
    }
  }

  public static String extractSub(String token) {
    if (StrUtil.isBlank(token)) return null;
    try {
      DecodedJWT decoded = JWT.decode(token);
      return decoded.getSubject();
    } catch (Exception e) {
      return null;
    }
  }
}
