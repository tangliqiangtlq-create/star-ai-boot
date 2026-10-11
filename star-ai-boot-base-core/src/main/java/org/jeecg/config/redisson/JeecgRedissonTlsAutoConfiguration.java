package org.jeecg.config.redisson;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.boot.starter.lock.config.RedissonConfiguration;
import org.jeecg.boot.starter.lock.enums.RedisConnectionType;
import org.jeecg.boot.starter.lock.prop.RedissonProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Overrides hardcoded {@code redis://} prefix in jeecg-boot-starter-lock; supports TLS ({@code rediss://})
 * and Redis ACL username (DCS §4.3).
 *
 * <pre>
 * jeecg:
 *   redisson:
 *     enabled: true
 *     ssl: true
 *     username: redis_system
 *     address: master.xxx.cache.amazonaws.com:6379
 *     password: "****"
 *     type: STANDALONE
 * </pre>
 *
 * Or set {@code address: rediss://host:6379} directly (ssl optional; still connects via TLS).
 *
 * <h2>Password resolution</h2>
 *
 * <p>{@code jeecg.redisson.password} is normally supplied by Nacos. When secrets are delivered only by
 * the G07 secrets backend ({@code /run/secrets} → Spring {@code Environment}, never OS env) the value may
 * be missing, empty, or an unresolved {@code ${...}} placeholder; in that case the password is resolved
 * from the injected secrets keys instead (see {@link #SECRET_PASSWORD_KEYS}). Without this fallback
 * Redisson silently connects without AUTH and fails with {@code NOAUTH Authentication required}.
 */
@Slf4j
@Configuration
@ConditionalOnClass({RedissonClient.class, RedissonProperties.class})
@ConditionalOnProperty(prefix = "jeecg.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
@AutoConfigureBefore(RedissonConfiguration.class)
public class JeecgRedissonTlsAutoConfiguration {

  /**
   * Whether TLS is enabled. Uses rediss:// when true; also uses TLS when address is already rediss://.
   */
  @Value("${jeecg.redisson.ssl:false}")
  private boolean ssl;

  /** Redis ACL username (DCS §4.3). Falls back to spring.redis.username. */
  @Value("${jeecg.redisson.username:${spring.redis.username:}}")
  private String username;

  @Bean(destroyMethod = "shutdown")
  public RedissonClient redissonClient(RedissonProperties properties, Environment environment) {
    boolean useSsl = resolveSsl(properties.getAddress(), ssl);
    String password = resolvePassword(environment, properties.getPassword());
    Config config = createConfig(properties, useSsl, username, password);
    log.info(
        "Redisson TLS config applied, type={}, ssl={}, username={}, address={}, auth={}",
        properties.getType(),
        useSsl,
        StringUtils.isNotBlank(username) ? username : "(default)",
        properties.getAddress(),
        password != null ? "username+password" : "none");
    if (password == null) {
      log.warn(
          "Redisson has no Redis password: checked jeecg.redisson.password and the G07 secrets keys {}. "
              + "If the server requires AUTH this client will fail with NOAUTH; provide "
              + "/run/secrets/jeecg.redisson.password (or JEECG_REDISSON_PASSWORD / SPRING_REDIS_PASSWORD) "
              + "through the secrets backend.",
          String.join(", ", SECRET_PASSWORD_KEYS));
    }
    return Redisson.create(config);
  }

  /**
   * Property names that may carry the Redis password.
   *
   * <p>{@code jeecg.redisson.password} is the bound starter-lock property (Nacos, or the K8s-style
   * per-key secret file of the same name). The remaining entries are the G07 secrets-backend keys that
   * {@link org.jeecg.common.env.secrets.MpaySecretsEnvironmentPostProcessor} injects into the
   * {@link Environment}; they are resolved with an exact-name lookup so Redis keeps authenticating even
   * when the Nacos value is absent, empty, or still an unresolved {@code ${...}} placeholder.
   */
  static final String[] SECRET_PASSWORD_KEYS = {
    "JEECG_REDISSON_PASSWORD",
    "SPRING_REDIS_PASSWORD",
    "SPRING_DATA_REDIS_PASSWORD",
    "spring.redis.password",
    "spring.data.redis.password"
  };

  static String resolvePassword(Environment environment, String boundPassword) {
    if (isUsableSecret(boundPassword)) {
      return boundPassword.trim();
    }
    if (environment == null) {
      return null;
    }
    for (String key : SECRET_PASSWORD_KEYS) {
      String value = environment.getProperty(key);
      if (isUsableSecret(value)) {
        return value.trim();
      }
    }
    return null;
  }

  /** Blank values and unresolved placeholders both mean "not actually provided". */
  private static boolean isUsableSecret(String value) {
    return StringUtils.isNotBlank(value) && !value.contains("${");
  }

  static boolean resolveSsl(String address, boolean sslFlag) {
    if (StringUtils.isNotBlank(address) && address.trim().startsWith("rediss://")) {
      return true;
    }
    return sslFlag;
  }

  static Config createConfig(
      RedissonProperties properties, boolean useSsl, String username, String password) {
    RedisConnectionType type = properties.getType();
    if (type == null) {
      type = RedisConnectionType.STANDALONE;
    }
    return switch (type) {
      case SENTINEL -> createSentinel(properties, useSsl, username, password);
      case CLUSTER -> createCluster(properties, useSsl, username, password);
      case MASTERSLAVE -> createMasterSlave(properties, useSsl, username, password);
      default -> createStandalone(properties, useSsl, username, password);
    };
  }

  private static Config createStandalone(
      RedissonProperties properties, boolean useSsl, String username, String password) {
    Config config = new Config();
    var server =
        config
            .useSingleServer()
            .setAddress(JeecgRedissonAddressUtils.toUri(properties.getAddress(), useSsl))
            .setDatabase(properties.getDatabase());
    applyAuth(server::setUsername, server::setPassword, username, password);
    return config;
  }

  private static Config createCluster(
      RedissonProperties properties, boolean useSsl, String username, String password) {
    Config config = new Config();
    String[] nodes = properties.getAddress().split(",");
    for (String node : nodes) {
      if (StringUtils.isBlank(node)) {
        continue;
      }
      config.useClusterServers().addNodeAddress(JeecgRedissonAddressUtils.toUri(node, useSsl));
    }
    var servers = config.useClusterServers();
    applyAuth(servers::setUsername, servers::setPassword, username, password);
    return config;
  }

  /**
   * Format: masterName,host1:port,host2:port,...
   */
  private static Config createSentinel(
      RedissonProperties properties, boolean useSsl, String username, String password) {
    Config config = new Config();
    String[] tokens = properties.getAddress().split(",");
    if (tokens.length < 2) {
      throw new IllegalArgumentException(
          "Sentinel mode address format should be: masterName,host:port[,host:port...]");
    }
    var servers =
        config
            .useSentinelServers()
            .setMasterName(tokens[0].trim())
            .setDatabase(properties.getDatabase());
    applyAuth(servers::setUsername, servers::setPassword, username, password);
    for (int i = 1; i < tokens.length; i++) {
      if (StringUtils.isNotBlank(tokens[i])) {
        servers.addSentinelAddress(JeecgRedissonAddressUtils.toUri(tokens[i], useSsl));
      }
    }
    return config;
  }

  /**
   * Format: masterHost:port,slave1:port,slave2:port,...
   */
  private static Config createMasterSlave(
      RedissonProperties properties, boolean useSsl, String username, String password) {
    Config config = new Config();
    String[] tokens = properties.getAddress().split(",");
    if (tokens.length < 1 || StringUtils.isBlank(tokens[0])) {
      throw new IllegalArgumentException("Master-replica mode address must configure at least master host:port");
    }
    var servers =
        config
            .useMasterSlaveServers()
            .setMasterAddress(JeecgRedissonAddressUtils.toUri(tokens[0], useSsl))
            .setDatabase(properties.getDatabase());
    applyAuth(servers::setUsername, servers::setPassword, username, password);
    for (int i = 1; i < tokens.length; i++) {
      if (StringUtils.isNotBlank(tokens[i])) {
        servers.addSlaveAddress(JeecgRedissonAddressUtils.toUri(tokens[i], useSsl));
      }
    }
    return config;
  }

  private static void applyAuth(
      java.util.function.Consumer<String> setUsername,
      java.util.function.Consumer<String> setPassword,
      String username,
      String password) {
    if (StringUtils.isNotBlank(username)) {
      setUsername.accept(username);
    }
    if (StringUtils.isNotBlank(password)) {
      setPassword.accept(password);
    }
  }
}
