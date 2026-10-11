package org.jeecg.modules.system.cache;

import me.zhyd.oauth.cache.AuthCacheConfig;
import me.zhyd.oauth.cache.AuthStateCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;


public class AuthStateRedisCache implements AuthStateCache {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private ValueOperations<String, String> valueOperations;

    @PostConstruct
    public void init() {
        valueOperations = redisTemplate.opsForValue();
    }

    /**
     * Put into cache with a default TTL of 3 minutes
     *
     * @param key   cache key
     * @param value cache value
     */
    @Override
    public void cache(String key, String value) {
        valueOperations.set(key, value, AuthCacheConfig.timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Put into cache
     *
     * @param key     cache key
     * @param value   cache value
     * @param timeout cache expiration time in milliseconds
     */
    @Override
    public void cache(String key, String value, long timeout) {
        valueOperations.set(key, value, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Get cache content
     *
     * @param key cache key
     * @return cache content
     */
    @Override
    public String get(String key) {
        return valueOperations.get(key);
    }

    /**
     * Whether the key exists; returns false if the value for the key has expired
     *
     * @param key cache key
     * @return true if key exists and value is not expired; false if key is missing or expired
     */
    @Override
    public boolean containsKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
