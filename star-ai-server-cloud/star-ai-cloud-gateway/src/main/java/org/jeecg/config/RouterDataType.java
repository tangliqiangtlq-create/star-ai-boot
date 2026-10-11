package org.jeecg.config;

/**
 * Nacos configuration mode enum
 * @author zyf
 * @date: 2022/4/21 10:55
 */
public enum RouterDataType {
    /**
     * Load route configuration from database
     */
    database,
    /**
     * Load route configuration from local yml
     */
    yml,
    /**
     * Load route configuration from Nacos.
     */
    nacos
}
