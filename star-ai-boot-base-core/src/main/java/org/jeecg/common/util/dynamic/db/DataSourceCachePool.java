package org.jeecg.common.util.dynamic.db;

import com.alibaba.druid.pool.DruidDataSource;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.system.vo.DynamicDataSourceModel;
import org.jeecg.common.util.SpringContextUtils;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.HashMap;
import java.util.Map;


/**
 * Data source cache pool
 * @author: jeecg-boot
 */
public class DataSourceCachePool {
    /** Data source connection pool cache [local class cache - distributed is not supported] */
    private static Map<String, DruidDataSource> dbSources = new HashMap<>();
    private static RedisTemplate<String, Object> redisTemplate;

    private static RedisTemplate<String, Object> getRedisTemplate() {
        if (redisTemplate == null) {
            redisTemplate = (RedisTemplate<String, Object>) SpringContextUtils.getBean("redisTemplate");
        }
        return redisTemplate;
    }

    /**
     * Get multi-datasource cache
     *
     * @param dbKey
     * @return
     */
    public static DynamicDataSourceModel getCacheDynamicDataSourceModel(String dbKey) {
        String redisCacheKey = CacheConstant.SYS_DYNAMICDB_CACHE + dbKey;
        if (getRedisTemplate().hasKey(redisCacheKey)) {
            return (DynamicDataSourceModel) getRedisTemplate().opsForValue().get(redisCacheKey);
        }
        CommonAPI commonApi = SpringContextUtils.getBean(CommonAPI.class);
        DynamicDataSourceModel dbSource = commonApi.getDynamicDbSourceByCode(dbKey);
        if (dbSource != null) {
            getRedisTemplate().opsForValue().set(redisCacheKey, dbSource);
        }
        return dbSource;
    }

    public static DruidDataSource getCacheBasicDataSource(String dbKey) {
        return dbSources.get(dbKey);
    }

    /**
     * Put data source into cache.
     *
     * @param dbKey
     * @param db
     */
    public static void putCacheBasicDataSource(String dbKey, DruidDataSource db) {
        dbSources.put(dbKey, db);
    }

    /**
     * Clear data source cache
     */
    public static void cleanAllCache() {
        //Close data source connection
        for(Map.Entry<String, DruidDataSource> entry : dbSources.entrySet()){
            String dbkey = entry.getKey();
            DruidDataSource druidDataSource = entry.getValue();
            if(druidDataSource!=null && druidDataSource.isEnable()){
                druidDataSource.close();
            }
            //Clear Redis cache
            getRedisTemplate().delete(CacheConstant.SYS_DYNAMICDB_CACHE + dbkey);
        }
        //Clear cache
        dbSources.clear();
    }

    public static void removeCache(String dbKey) {
        //Close data source connection
        DruidDataSource druidDataSource = dbSources.get(dbKey);
        if(druidDataSource!=null && druidDataSource.isEnable()){
            druidDataSource.close();
        }
        //Clear Redis cache
        getRedisTemplate().delete(CacheConstant.SYS_DYNAMICDB_CACHE + dbKey);
        //Clear cache
        dbSources.remove(dbKey);
    }

}
