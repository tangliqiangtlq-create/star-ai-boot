package org.jeecg.config.redis;

import static java.util.Collections.singletonMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.modules.redis.writer.ScanSafeJeecgRedisCacheWriter;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * CodePlan 36 / C2-3: {@code @Primary} CacheManager backed by {@link
 * ScanSafeJeecgRedisCacheWriter} (SCAN) so Spring {@code @CacheEvict} never uses upstream KEYS
 * writer from {@code jeecg-boot-common}.
 */
@Slf4j
@Configuration
public class ScanSafeRedisCacheManagerConfig {

  @Bean
  @Primary
  public CacheManager scanSafeCacheManager(LettuceConnectionFactory factory) {
    Jackson2JsonRedisSerializer<Object> jackson = jacksonSerializer();
    RedisCacheConfiguration defaults =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(6))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jackson));

    RedisCacheWriter writer = new ScanSafeJeecgRedisCacheWriter(factory, Duration.ofMillis(50L));
    log.info("[REDIS-SCAN] Primary CacheManager using ScanSafeJeecgRedisCacheWriter (no KEYS)");

    return RedisCacheManager.builder(writer)
        .cacheDefaults(defaults)
        .withInitialCacheConfigurations(
            singletonMap(
                CacheConstant.SYS_DICT_TABLE_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(10))
                    .disableCachingNullValues()
                    .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jackson))))
        .withInitialCacheConfigurations(
            singletonMap(
                CacheConstant.TEST_DEMO_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5))
                    .disableCachingNullValues()))
        .withInitialCacheConfigurations(
            singletonMap(
                CacheConstant.PLUGIN_MALL_RANKING,
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(24))
                    .disableCachingNullValues()))
        .withInitialCacheConfigurations(
            singletonMap(
                CacheConstant.PLUGIN_MALL_PAGE_LIST,
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(24))
                    .disableCachingNullValues()))
        .transactionAware()
        .build();
  }

  @SuppressWarnings("deprecation")
  private static Jackson2JsonRedisSerializer<Object> jacksonSerializer() {
    Jackson2JsonRedisSerializer<Object> serializer =
        new Jackson2JsonRedisSerializer<>(Object.class);
    ObjectMapper mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    serializer.setObjectMapper(mapper);
    return serializer;
  }
}
