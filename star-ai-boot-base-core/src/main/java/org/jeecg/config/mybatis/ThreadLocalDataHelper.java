package org.jeecg.config.mybatis;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @Description: local thread variable storage utility
 * @author: lsq
 * @date: March 25, 2022 11:42
 */
public class ThreadLocalDataHelper {
    /**
     * Thread-local variable
     */
    private static final ThreadLocal<ConcurrentHashMap> REQUEST_DATA = new ThreadLocal<>();

    /**
     * Store local parameters
     */
    private static final ConcurrentHashMap DATA_MAP = new ConcurrentHashMap<>();

    /**
     * Set request parameter
     *
     * @param key  parameter key
     * @param value parameter value
     */
    public static void put(String key, Object value) {
        if(ObjectUtil.isNotEmpty(value)) {
            DATA_MAP.put(key, value);
            REQUEST_DATA.set(DATA_MAP);
        }
    }

    /**
     * Get request parameter value
     *
     * @param key request parameter
     * @return
     */
    public static <T> T get(String key) {
        ConcurrentHashMap dataMap = REQUEST_DATA.get();
        if (CollectionUtils.isNotEmpty(dataMap)) {
            return (T) dataMap.get(key);
        }
        return null;
    }

    /**
     * Get request parameters
     *
     * @return request parameter MAP object
     */
    public static void clear() {
        DATA_MAP.clear();
        REQUEST_DATA.remove();
    }

}

